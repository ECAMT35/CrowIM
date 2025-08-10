package com.ecamt35.messageservice.websocket;

import com.ecamt35.messageservice.config.NodeName;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    public static final String USER_ONLINE_STATUS_KEY = "user:online:status";

    /**
     * 注册用户与通道绑定
     */
    public void registerUser(long userId, Channel channel) {
        if (userChannels.putIfAbsent(userId, channel) == null) {
            log.info("User {} registered with channel {}", userId, channel.remoteAddress());
            channel.attr(USER_ID_KEY).set(userId); //  设置用户 ID 属性
            channel.attr(REGISTERED_KEY).set(true); //  设置注册状态
            redisTemplate.opsForHash().put(USER_ONLINE_STATUS_KEY, String.valueOf(userId), NodeName.NODE_NAME);
        } else {
            log.warn("User {} already exists in registry", userId);
        }
    }

    /**
     * 注销用户并移除通道
     */
    public void unregisterUser(Channel channel) {
        Long userId = channel.attr(USER_ID_KEY).get();
        if (userId != null) {
            userChannels.remove(userId);
            channel.attr(USER_ID_KEY).set(null); //  清空用户 ID
            channel.attr(REGISTERED_KEY).set(false); //  清空注册状态
            redisTemplate.opsForHash().delete(USER_ONLINE_STATUS_KEY, String.valueOf(userId));
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
}