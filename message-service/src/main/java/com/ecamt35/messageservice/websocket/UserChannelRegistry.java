package com.ecamt35.messageservice.websocket;

import com.ecamt35.messageservice.constant.OfflineConnectConstant;
import com.ecamt35.messageservice.model.bo.OfflineNotificationBo;
import com.ecamt35.messageservice.util.BusinessException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class UserChannelRegistry {

    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    public static final AttributeKey<Boolean> REGISTERED_KEY = AttributeKey.valueOf("registered");
    public static final AttributeKey<String> DEVICE_ID_KEY = AttributeKey.valueOf("deviceId");

    // Redis Lock key
    public static final String USER_DEVICE_ID_ONLINE_KEY_PREFIX = "user:online-device-id:";
    public static final String LOCK_DEVICE_ID_KEY_PREFIX = "lock:device:id:";

    // 以 deviceId 为键，存储每个设备对应的 Channel，每个设备一个条目
    private final ConcurrentMap<String, Channel> deviceChannels = new ConcurrentHashMap<>();
    private final ConcurrentMap<ChannelId, Channel> channelMap = new ConcurrentHashMap<>();

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

        String lockKey = LOCK_DEVICE_ID_KEY_PREFIX + deviceId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 15, TimeUnit.SECONDS)) {
                log.error("Failed to acquire lock for device {}, registration aborted", deviceId);
                throw new BusinessException("Device is being registered by another node, please retry later");
            }

            // 检查设备是否已在其他节点在线
            String onlineKey = USER_DEVICE_ID_ONLINE_KEY_PREFIX + userId + ":" + deviceId;
            String oldNode = redisTemplate.opsForValue().get(onlineKey);
            if (oldNode != null && !nodeName.equals(oldNode)) {
                // 设备在其他节点在线，发送下线通知
                OfflineNotificationBo notification = new OfflineNotificationBo(
                        userId, deviceId, oldNode, "new_connection"
                );
                rabbitTemplate.convertAndSend(
                        offlineConnectConstant.getOfflineConnectExchange(),
                        "offline-connect-" + oldNode,  // 目标节点的专用路由键
                        notification
                );
                log.info("Sent offline notification to node {} for device {}", oldNode, deviceId);
            }

            // 关闭本节点可能存在的旧连接（同一设备重复注册）
            Channel oldChannel = deviceChannels.get(deviceId);

            if (oldChannel != null) {
                log.info("User {} device {} already connected on this node, closing old channel", userId, deviceId);
                deviceChannels.remove(deviceId);
                channelMap.remove(oldChannel.id());
                if (oldChannel.isActive()) {
                    oldChannel.attr(USER_ID_KEY).set(null);
                    oldChannel.attr(DEVICE_ID_KEY).set(null);
                    oldChannel.attr(REGISTERED_KEY).set(false);
                    oldChannel.close();
                    log.info("Closed old local connection for device {}", deviceId);
                }
            }

            // 绑定新连接到本地缓存
            channel.attr(USER_ID_KEY).set(userId);
            channel.attr(DEVICE_ID_KEY).set(deviceId);
            channel.attr(REGISTERED_KEY).set(true);
            deviceChannels.put(deviceId, channel);
            channelMap.put(channel.id(), channel);

            redisTemplate.opsForValue().set(onlineKey, nodeName, 2, TimeUnit.MINUTES);

            log.info("User {} device {} registered successfully on node {}", userId, deviceId, nodeName);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for device {}", deviceId, e);
            throw new BusinessException(e.getMessage());
        } catch (RuntimeException e) {
            deviceChannels.remove(deviceId);
            channelMap.remove(channel.id());

            channel.attr(USER_ID_KEY).set(null);
            channel.attr(DEVICE_ID_KEY).set(null);
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
        if (deviceId == null) {
            log.info("Unregistration skipped: channel not bound to any device, channelId={}", channel.id().asShortText());
            return;
        }

        // 获取分布式锁
        String lockKey = LOCK_DEVICE_ID_KEY_PREFIX + deviceId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                log.error("Failed to acquire lock for device {}, unregistration aborted", deviceId);
                throw new BusinessException("Device is busy, please retry later");
            }

            // 先删 Redis，再清本地缓存
            String onlineKey = USER_DEVICE_ID_ONLINE_KEY_PREFIX + userId + ":" + deviceId;
            try {
                redisTemplate.delete(onlineKey);
            } catch (Exception e) {
                log.error("Failed to delete Redis online key for device {}, but continuing local cleanup", deviceId, e);
            }

            deviceChannels.remove(deviceId);
            channelMap.remove(channel.id());

            channel.attr(USER_ID_KEY).set(null);
            channel.attr(DEVICE_ID_KEY).set(null);
            channel.attr(REGISTERED_KEY).set(false);


            log.info("User {} device {} unregistered from channel {}", userId, deviceId, channel.remoteAddress());

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
    public Channel getRegisteredChannel(String deviceId) {
        return deviceChannels.get(deviceId);
    }

    /**
     * 根据 ChannelId 获取 Channel
     */
    public Channel getChannel(ChannelId channelId) {
        if (channelId == null) {
            log.info("Query failed: ChannelId is null");
            return null;
        }
        Channel channel = channelMap.get(channelId);
        if (channel == null) {
            log.info("Channel not found: channelId={}", channelId.asShortText());
        }
        return channel;
    }

    @PreDestroy
    public void destroy() {
        // 清空所有容器
        deviceChannels.clear();
        channelMap.clear();
    }
}