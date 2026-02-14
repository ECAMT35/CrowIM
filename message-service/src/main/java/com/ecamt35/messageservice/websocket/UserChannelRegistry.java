package com.ecamt35.messageservice.websocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.util.AttributeKey;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class UserChannelRegistry {

    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");
    public static final AttributeKey<Boolean> REGISTERED_KEY = AttributeKey.valueOf("registered");

    private final ConcurrentMap<Long, Channel> userChannels = new ConcurrentHashMap<>();
    private final ConcurrentMap<ChannelId, Channel> channelMap = new ConcurrentHashMap<>();

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Value("${node-name}")
    private String nodeName;

    public static final String USER_ONLINE_STATUS_KEY = "user:online:status";

    /**
     * 注册用户与通道绑定
     */
    public void registerUser(long userId, Channel channel) {
        if (channel == null || !channel.isActive() || !channel.isWritable()) {
            log.info("Registration failed: invalid channel, userId={}", userId);
            return;
        }

        // todo
        // 后续考虑多端登陆，所以暂不加分布式锁，但是到时候可能要改一下Redis的登陆名称
        // 仅锁定当前用户，相同userId竞争同一把锁，不同userId无竞争
        synchronized (String.valueOf(userId).intern()) {
            Channel oldChannel = userChannels.get(userId);
            // 清理旧连接（顶号逻辑, 不然新的设备无法注册）
            if (oldChannel != null) {
                log.info("User {} already online, closing old channel", userId);
                oldChannel.attr(USER_ID_KEY).set(null);
                oldChannel.attr(REGISTERED_KEY).set(false);
                channelMap.remove(oldChannel.id());
                oldChannel.close();
            }

            // 原子更新
            channel.attr(USER_ID_KEY).set(userId);
            channel.attr(REGISTERED_KEY).set(true);
            userChannels.put(userId, channel);
            channelMap.put(channel.id(), channel);
            redisTemplate.opsForHash().put(USER_ONLINE_STATUS_KEY, String.valueOf(userId), nodeName);

            log.info("User {} registered successfully", userId);
        }
    }

    /**
     * 注销用户并移除通道
     */
    public void unregisterUser(Channel channel) {

        if (channel == null) {
            return;
        }
        Long userId = channel.attr(USER_ID_KEY).get();
        if (userId == null) {
            log.info("Unregistration failed: channel not bound to any user，channelId={}", channel.id().asShortText());
            return;
        }

        // 与注册使用同一把锁
        synchronized (String.valueOf(userId).intern()) {
            // 先删Redis，再清本地缓存
            redisTemplate.opsForHash().delete(USER_ONLINE_STATUS_KEY, String.valueOf(userId));
            userChannels.remove(userId);
            channelMap.remove(channel.id());
            channel.attr(USER_ID_KEY).set(null); //  清空用户 ID
            channel.attr(REGISTERED_KEY).set(false); //  清空注册状态
            log.info("User {} unregistered from channel {}", userId, channel.remoteAddress());
        }

    }

    /**
     * 获取通道绑定的用户 ID
     */
    public Long getUserId(Channel channel) {
        return channel.attr(USER_ID_KEY).get();
    }

    /**
     * 获取指定用户的通道
     */
    public Channel getRegisteredChannel(long userId) {
        return userChannels.get(userId);
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
        userChannels.clear();
        channelMap.clear();
    }
}