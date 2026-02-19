package com.ecamt35.messageservice.websocket;

import cn.hutool.core.lang.Snowflake;
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
import java.util.concurrent.*;

@Component
@Slf4j
public class UserChannelRegistry {

    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    public static final AttributeKey<Boolean> REGISTERED_KEY = AttributeKey.valueOf("registered");
    public static final AttributeKey<String> DEVICE_ID_KEY = AttributeKey.valueOf("deviceId");
    public static final AttributeKey<String> SESSION_ID_KEY = AttributeKey.valueOf("sessionId");

    // 同一 channel 重复 REGISTER（VT1/VT2乱序）; handlerRemoved/unregister 已发生，但 VT 晚到仍想提交
    // 这些都是多余的操作，加一个版本号即可完成判断，当 channel 实时的 token 与创建 VT1 时的不一样就不执行了，节省IO、CPU
    public static final AttributeKey<Long> REG_TOKEN_KEY = AttributeKey.valueOf("regToken");

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
    @Resource
    private Snowflake snowflake;
    @Resource(name = "virtualExecutor")
    private ExecutorService virtualExecutor;
    @Value("${node-name}")
    private String nodeName;
    @Value("${device-session-timeout}")
    private int deviceSessionTimeout;

    /**
     * 异步发起注册流程：
     * 1) 在 eventLoop 内写入本次注册的 attrs/token，并做幂等与“正在注册”判断
     * 2) 将阻塞/IO 的注册流程交给虚拟线程执行（分布式锁 + Redis 路由 + MQ 踢旧）
     */
    public CompletableFuture<Void> registerUserAsync(Long userId, String deviceId, Channel channel) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        if (userId == null || deviceId == null || deviceId.isBlank()) {
            future.completeExceptionally(new BusinessException("Invalid userId or deviceId"));
            return future;
        }
        if (channel == null) {
            future.completeExceptionally(new BusinessException("Channel is null"));
            return future;
        }

        final String newSessionId = channel.id().asLongText();
        final Long regToken = snowflake.nextId();
        final String redisKey = wsOnlineKey(userId, deviceId);
        final String lockKey = LOCK_USER_DEVICE_KEY_PREFIX + userId + ":" + deviceId;

        // 先在 eventLoop 写入本次注册标识 + 基础 attrs，保证和 handlerRemoved 的顺序一致
        // 同一个channel串行，做一些判断基本并发问题不大
        channel.eventLoop().execute(() -> {
            if (!channel.isActive()) {
                future.completeExceptionally(new BusinessException("Channel inactive"));
                return;
            }
            Boolean reg = channel.attr(REGISTERED_KEY).get();
            Long existedUser = channel.attr(USER_ID_KEY).get();
            String existedDev = channel.attr(DEVICE_ID_KEY).get();

            // 重复 REGISTER 幂等
            if (Boolean.TRUE.equals(reg)
                    && existedUser != null && existedUser.equals(userId)
                    && existedDev != null && existedDev.equals(deviceId)) {
                future.complete(null);
                return;
            }

            // 避免重复 REGISTER 反复启动虚拟线程
            // 判断 REGISTERED != true 且 token 已存在，说明已经有一次注册尝试在跑
            Long curToken = channel.attr(REG_TOKEN_KEY).get();
            if (curToken != null && !Boolean.TRUE.equals(reg)) {
                future.completeExceptionally(new BusinessException("Register in progress"));
                return;
            }

            channel.attr(REG_TOKEN_KEY).set(regToken);
            channel.attr(USER_ID_KEY).set(userId);
            channel.attr(DEVICE_ID_KEY).set(deviceId);
            channel.attr(SESSION_ID_KEY).set(newSessionId);
            channel.attr(REGISTERED_KEY).set(false);

            // 阻塞部分丢到虚拟线程
            virtualExecutor.execute(() -> doRegisterInVirtualThread(userId, deviceId, channel, newSessionId, regToken, lockKey, redisKey, future));
        });

        return future;
    }

    /**
     *
     * 虚拟线程中的“完整注册事务”：
     * 1. 获取分布式锁
     * 2. 读取旧路由(node/sessionId)
     * 3. 校验本次注册 token 仍有效
     * 4. 在 eventLoop 绑定本地映射并关闭本机旧连接
     * 5. 写 Redis 路由并设置 TTL
     * 6. 在 eventLoop 标记 registered=true
     * 7. 必要时发送 MQ 通知旧节点踢旧连接
     * 8. complete future
     *
     */
    private void doRegisterInVirtualThread(Long userId, String deviceId, Channel channel, String newSessionId,
                                           Long regToken, String lockKey, String redisKey, CompletableFuture<Void> future) {

        RLock lock = redissonClient.getLock(lockKey);

        String oldNode = null;
        String oldSessionId = null;

        try {
            if (!lock.tryLock(5, 15, TimeUnit.SECONDS)) {
                throw new BusinessException("Device is being registered by another node, please retry later");
            }

            // 旧路由
            Object oldNodeObj = redisTemplate.opsForHash().get(redisKey, "node");
            Object oldSidObj = redisTemplate.opsForHash().get(redisKey, "sessionId");
            oldNode = oldNodeObj == null ? null : String.valueOf(oldNodeObj);
            oldSessionId = oldSidObj == null ? null : String.valueOf(oldSidObj);

            // 如果这次注册已经失效（连接断了/被新注册覆盖），直接失败
            if (!isAttemptValid(channel, regToken)) {
                throw new BusinessException("Channel closed or registration superseded");
            }

            // 先本地 commit，再写 Redis，避免Redis TTL垃圾
            boolean committed = bindChannelAndCloseOldOnEventLoop(userId, deviceId, channel, regToken);
            if (!committed) {
                cleanupLocalOnEventLoop(userId, deviceId, channel, regToken);
                throw new BusinessException("Channel closed before commit, registration aborted");
            }

            if (!isAttemptValid(channel, regToken)) {
                cleanupLocalOnEventLoop(userId, deviceId, channel, regToken);
                throw new BusinessException("Registration superseded after commit");
            }

            try {
                Map<String, Object> hashData = new HashMap<>();
                hashData.put("node", nodeName);
                hashData.put("sessionId", newSessionId);
                hashData.put("ts", String.valueOf(System.currentTimeMillis()));
                redisTemplate.opsForHash().putAll(redisKey, hashData);
                redisTemplate.expire(redisKey, deviceSessionTimeout, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Redis 写失败时，抛出异常清理本地
                throw new BusinessException("Write redis route failed: " + e.getMessage());
            }

            CompletableFuture<Void> markDone = new CompletableFuture<>();
            channel.eventLoop().execute(() -> {
                Long token = channel.attr(REG_TOKEN_KEY).get();
                if (channel.isActive() && token != null && token.equals(regToken)) {
                    channel.attr(REGISTERED_KEY).set(true);
                }
                markDone.complete(null);
            });
            markDone.join();

            // 提交成功后再踢旧，避免新连接失败却踢旧
            if (oldNode != null && !oldNode.isBlank() && oldSessionId != null && !oldSessionId.isBlank()
                    && !nodeName.equals(oldNode)) {

                if (isAttemptValid(channel, regToken)) {
                    OfflineNotificationBo notification = new OfflineNotificationBo(
                            userId, deviceId, oldNode, "new_connection", oldSessionId
                    );
                    rabbitTemplate.convertAndSend(
                            offlineConnectConstant.getOfflineConnectExchange(),
                            "offline-connect-" + oldNode,
                            notification
                    );
                }
            }

            future.complete(null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupLocalOnEventLoop(userId, deviceId, channel, regToken);
            future.completeExceptionally(new BusinessException("Lock interrupted"));
        } catch (Exception e) {
            cleanupLocalOnEventLoop(userId, deviceId, channel, regToken);
            future.completeExceptionally(e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 将channel映射到deviceChannels
     * 关闭本地可能的旧连接
     * 返回 true 表示提交成功，false 表示 channel 已断或 token 不匹配
     */
    private boolean bindChannelAndCloseOldOnEventLoop(Long userId, String deviceId, Channel channel, Long regToken) {

        CompletableFuture<Boolean> f = new CompletableFuture<>();

        channel.eventLoop().execute(() -> {
            if (!channel.isActive()) {
                f.complete(false);
                return;
            }
            Long token = channel.attr(REG_TOKEN_KEY).get();
            if (token == null || !token.equals(regToken)) {
                f.complete(false);
                return;
            }

            String lk = localKey(userId, deviceId);
            Channel oldChannel = deviceChannels.put(lk, channel);

            // 关闭本地旧连接（同 device 重连/顶号）
            if (oldChannel != null && oldChannel != channel) {
                oldChannel.attr(USER_ID_KEY).set(null);
                oldChannel.attr(DEVICE_ID_KEY).set(null);
                oldChannel.attr(SESSION_ID_KEY).set(null);
                oldChannel.attr(REGISTERED_KEY).set(false);
                oldChannel.attr(REG_TOKEN_KEY).set(null);

                oldChannel.eventLoop().execute(() -> {
                    if (oldChannel.isActive()) oldChannel.close();
                });
            }

            f.complete(true);
        });

        // 阻塞等待 NettyIO 线程完成
        return f.join();
    }

    /**
     * 异步触发注销：
     * 1. eventLoop 内移除本地映射、清理 attrs
     * 2. 虚拟线程中加锁并在 Redis 侧做 session 匹配才删除的路由清理
     */
    public void unregisterAsync(Channel channel) {

        if (channel == null) {
            return;
        }

        channel.eventLoop().execute(() -> {
            Long userId = channel.attr(USER_ID_KEY).get();
            String deviceId = channel.attr(DEVICE_ID_KEY).get();
            String sessionId = channel.attr(SESSION_ID_KEY).get();

            if (userId != null && deviceId != null) {
                // 只在 map 仍指向当前 channel 时才删除，避免旧连接误删新连接
                deviceChannels.remove(localKey(userId, deviceId), channel);

                // 清掉 token，防止异步 register 晚到复活绑定
                channel.attr(REG_TOKEN_KEY).set(null);
                channel.attr(REGISTERED_KEY).set(false);
                channel.attr(USER_ID_KEY).set(null);
                channel.attr(DEVICE_ID_KEY).set(null);
                channel.attr(SESSION_ID_KEY).set(null);

                // Redis 删除放虚拟线程
                virtualExecutor.execute(() -> deleteRedisRoute(userId, deviceId, sessionId));
            }
        });

    }

    private void deleteRedisRoute(Long userId, String deviceId, String sessionId) {
        String lockKey = LOCK_USER_DEVICE_KEY_PREFIX + userId + ":" + deviceId;
        RLock lock = redissonClient.getLock(lockKey);

        String redisKey = wsOnlineKey(userId, deviceId);

        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                return;
            }

            Object sidObj = redisTemplate.opsForHash().get(redisKey, "sessionId");
            String redisSid = sidObj == null ? null : String.valueOf(sidObj);

            // session 匹配才删，避免误删新会话
            if (sessionId != null && sessionId.equals(redisSid)) {
                redisTemplate.delete(redisKey);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private void cleanupLocalOnEventLoop(Long userId, String deviceId, Channel channel, Long regToken) {
        channel.eventLoop().execute(() -> {
            deviceChannels.remove(localKey(userId, deviceId), channel);

            Long token = channel.attr(REG_TOKEN_KEY).get();
            if (token != null && token.equals(regToken)) {
                channel.attr(REG_TOKEN_KEY).set(null);
                channel.attr(REGISTERED_KEY).set(false);
                channel.attr(USER_ID_KEY).set(null);
                channel.attr(DEVICE_ID_KEY).set(null);
                channel.attr(SESSION_ID_KEY).set(null);
            }
        });
    }

    private boolean isAttemptValid(Channel channel, Long regToken) {
        if (channel == null || !channel.isActive()) return false;
        Long token = channel.attr(REG_TOKEN_KEY).get();
        return token != null && token.equals(regToken);
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