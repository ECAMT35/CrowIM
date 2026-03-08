package com.ecamt35.messageservice.service;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.model.bo.RelationPushBo;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.util.BusinessException;
import com.ecamt35.messageservice.websocket.UserChannelRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class RelationDeliveryService {

    private static final String USER_DEVICES = "user:devices:";

    private final UserChannelRegistry userChannelRegistry;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    @Value("${node-name}")
    private String nodeName;

    public RelationDeliveryService(UserChannelRegistry userChannelRegistry,
                                   RabbitTemplate rabbitTemplate,
                                   @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
                                   ObjectMapper objectMapper) {
        this.userChannelRegistry = userChannelRegistry;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 将关系域事件投递到目标用户的在线设备（支持跨节点转发）。
     */
    public void deliverToUserDevices(RelationPushBo pushBo) {
        Long targetUserId = pushBo.getTargetUserId();
        if (targetUserId == null) return;

        String receiverDeviceId = Convert.toStr(pushBo.getReceiverDeviceId());
        if (receiverDeviceId != null && !receiverDeviceId.isBlank()) {
            deliverToOneDevice(targetUserId, receiverDeviceId, pushBo);
            return;
        }

        String userDevicesKey = USER_DEVICES + targetUserId;
        Map<Object, Object> deviceTokenMap = redisTemplate.opsForHash().entries(userDevicesKey);
        if (deviceTokenMap.isEmpty()) {
            return;
        }

        for (Map.Entry<Object, Object> entry : deviceTokenMap.entrySet()) {
            String deviceId = Convert.toStr(entry.getKey());
            if (deviceId == null || deviceId.isBlank()) continue;
            deliverToOneDevice(targetUserId, deviceId, pushBo);
        }
    }

    private void deliverToOneDevice(Long targetUserId, String deviceId, RelationPushBo pushBo) {
        String wsOnlineKey = userChannelRegistry.wsOnlineKey(targetUserId, deviceId);

        Object nodeObj = redisTemplate.opsForHash().get(wsOnlineKey, "node");
        Object sidObj = redisTemplate.opsForHash().get(wsOnlineKey, "sessionId");
        if (nodeObj == null) {
            return;
        }

        String node = Convert.toStr(nodeObj);
        String sessionId = Convert.toStr(sidObj);

        if (nodeName.equals(node)) {
            String pushJson;
            try {
                pushJson = objectMapper.writeValueAsString(new PushVo(pushBo.getPacketType(), pushBo.getData()));
            } catch (Exception e) {
                throw new BusinessException(e.getMessage());
            }

            Channel ch = userChannelRegistry.getRegisteredChannel(targetUserId, deviceId);
            if (ch == null || !ch.isActive() || !ch.isWritable()) return;

            Boolean reg = ch.attr(UserChannelRegistry.REGISTERED_KEY).get();
            if (reg == null || !reg) return;

            String localSid = ch.attr(UserChannelRegistry.SESSION_ID_KEY).get();
            if (sessionId != null && !sessionId.isBlank() && localSid != null && !localSid.equals(sessionId)) {
                return;
            }

            ch.eventLoop().execute(() -> ch.writeAndFlush(new TextWebSocketFrame(pushJson)));
            return;
        }

        RelationPushBo forwarded = new RelationPushBo(
                pushBo.getTargetUserId(),
                pushBo.getPacketType(),
                pushBo.getData(),
                deviceId
        );

        // 跨节点路由到目标节点专属交换机
        String routingKey = "websocket-relation-" + node;
        rabbitTemplate.convertAndSend(routingKey + ".direct", routingKey, forwarded);
    }
}
