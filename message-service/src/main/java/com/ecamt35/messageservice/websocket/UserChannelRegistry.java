package com.ecamt35.messageservice.websocket;

import com.ecamt35.messageservice.constant.OfflineConnectConstant;
import com.ecamt35.messageservice.model.bo.OfflineNotificationBo;
import com.ecamt35.messageservice.util.BusinessException;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class UserChannelRegistry {

    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    public static final AttributeKey<Boolean> REGISTERED_KEY = AttributeKey.valueOf("registered");
    public static final AttributeKey<String> DEVICE_ID_KEY = AttributeKey.valueOf("deviceId");
    public static final AttributeKey<String> SESSION_ID_KEY = AttributeKey.valueOf("sessionId");

    // Redis keys
    public static final String WS_ONLINE_KEY_PREFIX = "ws:online:"; // ws:online:{userId}:{deviceId}
    public static final String LOCK_USER_DEVICE_KEY_PREFIX = "lock:user:device:";

    // key 用 userId:deviceId，避免不同用户 deviceId 冲突
    private final ConcurrentMap<String, Channel> deviceChannels = new ConcurrentHashMap<>();

    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private OfflineConnectConstant offlineConnectConstant;
    @Value("${node-name}")
    private String nodeName;
    @Value("${device-session-timeout}")
    private int deviceSessionTimeout;


    /**
     * 注册用户与通道绑定
     */
    public void registerUser(Long userId, String deviceId, Channel channel) {

        if (userId == null || deviceId == null || deviceId.isBlank()) {
            log.warn("Registration failed: invalid userId or deviceId, userId={}, deviceId={}", userId, deviceId);
            throw new BusinessException("Invalid userId or deviceId");
        }
        if (channel == null || !channel.isActive() || !channel.isWritable()) {
            log.warn("Registration failed: invalid channel, userId={}, deviceId={}", userId, deviceId);
            throw new BusinessException("Invalid channel");
        }

        String lockKey = LOCK_USER_DEVICE_KEY_PREFIX + userId + ":" + deviceId;
        RLock lock = redissonClient.getLock(lockKey);

        // 当前连接 sessionId（ChannelId 长文本）
        final String newSessionId = channel.id().asLongText();

        try {
            if (!lock.tryLock(5, 15, TimeUnit.SECONDS)) {
                log.error("Failed to acquire lock for userId={}, deviceId={}, registration aborted", userId, deviceId);
                throw new BusinessException("Device is being registered by another node, please retry later");
            }

            String redisKey = wsOnlineKey(userId, deviceId);
            Object oldNodeObj = redisTemplate.opsForHash().get(redisKey, "node");
            Object oldSidObj = redisTemplate.opsForHash().get(redisKey, "sessionId");
            String oldNode = oldNodeObj == null ? null : String.valueOf(oldNodeObj);
            String oldSessionId = oldSidObj == null ? null : String.valueOf(oldSidObj);

            // 检查设备是否已在其他节点在线, 通知旧节点只下线 oldSessionId 那条连接
            if (oldNode != null && !oldNode.isBlank() && !nodeName.equals(oldNode)) {
                OfflineNotificationBo notification = new OfflineNotificationBo(
                        userId, deviceId, oldNode, "new_connection", oldSessionId
                );
                rabbitTemplate.convertAndSend(
                        offlineConnectConstant.getOfflineConnectExchange(),
                        "offline-connect-" + oldNode,
                        notification
                );
                log.info("Sent offline notify to node={}, userId={}, deviceId={}, oldSessionId={}",
                        oldNode, userId, deviceId, oldSessionId);
            }


            // 先本地绑定（attrs + deviceChannels）
            // 如果先放到 Redis，后面的操作失败了，Redis 路由也成了废数据
            // 不如先本地做好，Redis 路由失败了还能靠 catch 回滚，少了 Redis 上的废数据
            // 绑定新连接的属性
            channel.attr(USER_ID_KEY).set(userId);
            channel.attr(DEVICE_ID_KEY).set(deviceId);
            channel.attr(SESSION_ID_KEY).set(newSessionId);
            channel.attr(REGISTERED_KEY).set(true);

            String lk = localKey(userId, deviceId);
            Channel oldChannel = deviceChannels.put(lk, channel);

            // 关闭本地可能的旧连接
            if (oldChannel != null && oldChannel != channel) {
                log.info("User {} device {} already connected on this node, closing old channel", userId, deviceId);

                oldChannel.attr(USER_ID_KEY).set(null);
                oldChannel.attr(DEVICE_ID_KEY).set(null);
                oldChannel.attr(SESSION_ID_KEY).set(null);
                oldChannel.attr(REGISTERED_KEY).set(false);

                // close 切回旧连接的 eventLoop
                oldChannel.eventLoop().execute(() -> {
                    if (oldChannel.isActive()) {
                        oldChannel.close();
                    }
                });
            }

            // Redis 在线信息更新
            Map<String, Object> hashData = new HashMap<>();
            hashData.put("node", nodeName);
            hashData.put("sessionId", newSessionId);
            hashData.put("ts", String.valueOf(System.currentTimeMillis()));

            redisTemplate.opsForHash().putAll(redisKey, hashData);
            redisTemplate.expire(redisKey, deviceSessionTimeout, TimeUnit.SECONDS);

            log.info("User {} device {} registered on node {}, sessionId={}", userId, deviceId, nodeName, newSessionId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for device {}", deviceId, e);
            throw new BusinessException(e.getMessage());
        } catch (RuntimeException e) {
            // 回滚，仍然指向当前 channel 时才删除，避免误删新连接
            deviceChannels.remove(localKey(userId, deviceId), channel);
            channel.attr(USER_ID_KEY).set(null);
            channel.attr(DEVICE_ID_KEY).set(null);
            channel.attr(SESSION_ID_KEY).set(null);
            channel.attr(REGISTERED_KEY).set(false);
            throw new BusinessException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 注销用户并移除通道
     */
    public void unregisterUser(Channel channel) {
        if (channel == null) {
            throw new BusinessException("Channel cannot be null");
        }

        Long userId = channel.attr(USER_ID_KEY).get();
        String deviceId = channel.attr(DEVICE_ID_KEY).get();
        String sessionId = channel.attr(SESSION_ID_KEY).get();

        if (userId == null || deviceId == null) {
            log.warn("Unregistration skipped: channel not bound, channelId={}", channel.id().asShortText());
            return;
        }

        // 获取分布式锁
        String lockKey = LOCK_USER_DEVICE_KEY_PREFIX + userId + ":" + deviceId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                log.error("Failed to acquire lock for device {}, unregistration aborted", deviceId);
                throw new BusinessException("Device is busy, please retry later");
            }

            // 先删 Redis，再清本地缓存
            // 只删除匹配当前 sessionId 的 redis 在线信息，避免误删新会话
            String redisKey = wsOnlineKey(userId, deviceId);
            Object sidObj = redisTemplate.opsForHash().get(redisKey, "sessionId");
            String redisSid = sidObj == null ? null : String.valueOf(sidObj);
            if (sessionId != null && sessionId.equals(redisSid)) {
                try {
                    redisTemplate.delete(redisKey);
                } catch (Exception e) {
                    log.error("Failed to delete Redis online key for device {}, sessionId: {}, but continuing local cleanup",
                            deviceId, sessionId, e);
                }
            } else {
                log.info("Skip delete redis ws online because session mismatch, userId={}, deviceId={}, local={}, redis={}",
                        userId, deviceId, sessionId, redisSid);
            }

            // 只在 map 里仍然是当前 channel 时才 remove，避免旧连接 close 误删新连接
            deviceChannels.remove(localKey(userId, deviceId), channel);

            channel.attr(USER_ID_KEY).set(null);
            channel.attr(DEVICE_ID_KEY).set(null);
            channel.attr(SESSION_ID_KEY).set(null);
            channel.attr(REGISTERED_KEY).set(false);

            log.info("User {} device {} unregistered, sessionId={}, remote={}", userId, deviceId, sessionId, channel.remoteAddress());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for device {}", deviceId, e);
            throw new BusinessException(e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 获取通道绑定的用户 ID
     */
    public Long getUserId(Channel channel) {
        return channel.attr(USER_ID_KEY).get();
    }

    /**
     * 获取指定设备的通道
     */
    public Channel getRegisteredChannel(Long userId, String deviceId) {
        return deviceChannels.get(localKey(userId, deviceId));
    }

    public String localKey(Long userId, String deviceId) {
        return userId + ":" + deviceId;
    }

    public String wsOnlineKey(Long userId, String deviceId) {
        return WS_ONLINE_KEY_PREFIX + userId + ":" + deviceId;
    }

    @PreDestroy
    public void destroy() {
        deviceChannels.clear();
    }
}