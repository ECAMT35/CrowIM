package com.ecamt35.messageservice.websocket;

import com.ecamt35.messageservice.config.NodeName;
import com.ecamt35.messageservice.model.bo.SendMessageBo;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import static com.ecamt35.messageservice.websocket.UserChannelRegistry.USER_ONLINE_STATUS_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final UserChannelRegistry userChannelRegistry;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 注册用户与通道绑定
     */
    public void registerUser(long userId, Channel channel) {
        userChannelRegistry.registerUser(userId, channel);
    }

    /**
     * 注销用户并移除通道
     */
    public void unregisterUser(Channel channel) {
        userChannelRegistry.unregisterUser(channel);
    }

    /**
     * 获取通道绑定的用户 ID
     */
    public Long getUserId(Channel channel) {
        return userChannelRegistry.getUserId(channel);
    }

    /**
     * 向指定用户发送消息
     */
    public void sendMessageToUser(SendMessageBo sendMessageBo) {
        Channel channel = userChannelRegistry.getRegisteredChannel(sendMessageBo.getTargetUserId());

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(sendMessageBo.getMessageId());
        stringBuilder.append(":");
        stringBuilder.append(sendMessageBo.getSenderId());
        stringBuilder.append(":");
        stringBuilder.append(sendMessageBo.getTimestamp());
        stringBuilder.append(":");
        stringBuilder.append(sendMessageBo.getMessage());

        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new TextWebSocketFrame(stringBuilder.toString()));
            return;
        }

        // 重新检查节点是否为本机
        String nodeKey = (String) redisTemplate.opsForHash().get(
                USER_ONLINE_STATUS_KEY,
                String.valueOf(sendMessageBo.getTargetUserId())
        );

        if (NodeName.NODE_NAME.equals(nodeKey)) {
            // 再次尝试获取Channel
            channel = userChannelRegistry.getRegisteredChannel(sendMessageBo.getTargetUserId());
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(stringBuilder.toString()));
                return;
            }

            // exist?
            // send to DB, not fount status in redis
            // todo

        } else {
            // 转发到目标节点
            StringBuilder sb = new StringBuilder();
            sb.append("websocket-message-");
            sb.append(nodeKey);
            String exchange = sb + ".direct";
            String key = sb.toString();
            rabbitTemplate.convertAndSend(
                    exchange,
                    key,
                    sendMessageBo
            );

        }

    }

    /**
     * 向MQ发送消息
     */
    public void sendMessageToMQ(SendMessageBo sendMessageBo) {

        String nodeKey = (String) redisTemplate.opsForHash().get(
                USER_ONLINE_STATUS_KEY,
                String.valueOf(sendMessageBo.getTargetUserId())
        );

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("websocket-message-");
        stringBuilder.append(nodeKey);
        String exchange = stringBuilder + ".direct";
        String key = stringBuilder.toString();

        if (nodeKey != null) {
            rabbitTemplate.convertAndSend(
                    exchange,
                    key,
                    sendMessageBo
            );
        } else {
            // exist?
            // send to DB, not fount status in redis
            // todo

        }


    }
}
