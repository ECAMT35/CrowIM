package com.ecamt35.messageservice.websocket;

import com.ecamt35.messageservice.config.NodeName;
import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.bo.SendMessageBo;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
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

    @Resource
    private UserChannelRegistry userChannelRegistry;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private ObjectMapper objectMapper;

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

        // 统一返回格式
        PushVo pushVo = new PushVo(PacketTypeConstant.SERVER_REQUEST_SENT, sendMessageBo);
        String pushVoJson;
        try {
            pushVoJson = objectMapper.writeValueAsString(pushVo);
        } catch (JsonProcessingException e) {
            log.error("Invalid message format: {}", sendMessageBo);
            throw new RuntimeException(e);
        }

        // 重新检查节点是否为本机
        String nodeKey = (String) redisTemplate.opsForHash().get(
                USER_ONLINE_STATUS_KEY,
                String.valueOf(sendMessageBo.getReceiverId())
        );
        if (nodeKey == null || nodeKey.isBlank()) {
            log.info("User {} is offline, skip forward message", sendMessageBo.getReceiverId());
            return;
        }

        Channel channel;
        if (NodeName.NODE_NAME.equals(nodeKey)) {
            // 尝试获取Channel
            channel = userChannelRegistry.getRegisteredChannel(sendMessageBo.getReceiverId());
            if (channel != null && channel.isActive() && channel.isWritable()) {
                channel.eventLoop().execute(() -> channel.writeAndFlush(new TextWebSocketFrame(pushVoJson)));
            }
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
     * 根据 ChannelId 获取 Channel
     */
    public Channel getChannel(ChannelId channelId) {
        if (channelId == null) {
            log.warn("ChannelId is null");
            return null;
        }
        return userChannelRegistry.getChannel(channelId);
    }
}
