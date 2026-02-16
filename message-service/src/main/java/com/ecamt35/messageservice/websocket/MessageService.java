package com.ecamt35.messageservice.websocket;

import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.bo.SendMessageBo;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
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

    @Value("${node-name}")
    private String nodeName;

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

        // todo 获取所有登录设备，查询是否在线,在线的就推送

        // todo 然后查询这些设备是否在线，在线的设备就直接推送消息

        // 重新检查节点是否为本机
//        String nodeKey = (String) redisTemplate.opsForHash().get(
//                USER_ONLINE_STATUS_KEY,
//                String.valueOf(sendMessageBo.getReceiverId())
//        );
//        if (nodeKey == null || nodeKey.isBlank()) {
//            log.info("User {} is offline, skip forward message", sendMessageBo.getReceiverId());
//            return;
//        }
//
//        Channel channel;
//        if (nodeName.equals(nodeKey)) {
//            // 尝试获取Channel
//            channel = userChannelRegistry.getRegisteredChannel(sendMessageBo.getReceiverId());
//            if (channel != null && channel.isActive() && channel.isWritable()) {
//                channel.eventLoop().execute(() -> channel.writeAndFlush(new TextWebSocketFrame(pushVoJson)));
//            }
//        } else {
//            // 转发到目标节点
//            StringBuilder sb = new StringBuilder();
//            sb.append("websocket-message-");
//            sb.append(nodeKey);
//            String exchange = sb + ".direct";
//            String key = sb.toString();
//            rabbitTemplate.convertAndSend(
//                    exchange,
//                    key,
//                    sendMessageBo
//            );
//        }
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
