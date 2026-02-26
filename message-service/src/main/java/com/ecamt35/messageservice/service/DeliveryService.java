package com.ecamt35.messageservice.service;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.bo.SendMessageBo;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.util.BusinessException;
import com.ecamt35.messageservice.websocket.UserChannelRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    static final String USER_DEVICES = "user:devices:";

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
     * 向指定用户发送消息
     */
    public void deliverToUserDevices(SendMessageBo sendMessageBo) {
        PushVo pushVo = new PushVo(PacketTypeConstant.SERVER_REQUEST_SENT, sendMessageBo);

        final String pushJson;
        try {
            pushJson = objectMapper.writeValueAsString(pushVo);
        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }

        Long targetUserId = sendMessageBo.getTargetUserId();
        if (targetUserId == null) {
            log.warn("targetUserId is null, skip");
            return;
        }

        // 单设备（跨节点回来的消息会带 receiverDeviceId）
        String receiverDeviceId = Convert.toStr(sendMessageBo.getReceiverDeviceId());
        if (receiverDeviceId != null && !receiverDeviceId.isBlank()) {
            log.info("Single device push,device id:{}", receiverDeviceId);
            deliverToOneDevice(targetUserId, receiverDeviceId, pushJson, sendMessageBo);
            return;
        }

        // 多设备：遍历设备列表
        // todo 后续从用户模块做成 rpc 调用, 使用 lua 脚本,直接查询返回设备列表的在线设备, 查询 ws 在线节点信息并投递，暂时不做
        String userDevicesKey = USER_DEVICES + targetUserId;
        Map<Object, Object> deviceTokenMap = redisTemplate.opsForHash().entries(userDevicesKey);
        if (deviceTokenMap.isEmpty()) {
            log.info("User {} has no login devices in {}, skip", targetUserId, userDevicesKey);
            return;
        }

        log.info("Multimodal device push, receiverId:{}", targetUserId);
        // 遍历每台设备，查询 ws 在线节点信息并投递
        for (Map.Entry<Object, Object> entry : deviceTokenMap.entrySet()) {
            String deviceId = Convert.toStr(entry.getKey());
            if (deviceId == null || deviceId.isBlank()) continue;
            deliverToOneDevice(targetUserId, deviceId, pushJson, sendMessageBo);
        }
    }

    /**
     * 使用 deviceId 向本地的 Channel 推送信息
     */
    private void deliverToOneDevice(Long targetUserId,
                                    String deviceId,
                                    String pushJson,
                                    SendMessageBo sendMessageBo) {

        String wsOnlineKey = userChannelRegistry.wsOnlineKey(targetUserId, deviceId);

        // todo lua
        Object nodeObj = redisTemplate.opsForHash().get(wsOnlineKey, "node");
        Object sidObj = redisTemplate.opsForHash().get(wsOnlineKey, "sessionId");
        if (nodeObj == null) {
            // 说明该 deviceId 不在线
            // 不在线不推
            return;
        }

        String node = Convert.toStr(nodeObj);
        String sessionId = Convert.toStr(sidObj);

        if (nodeName.equals(node)) {
            Channel ch = userChannelRegistry.getRegisteredChannel(targetUserId, deviceId);
            if (ch == null || !ch.isActive() || !ch.isWritable()) return;

            Boolean reg = ch.attr(UserChannelRegistry.REGISTERED_KEY).get();
            if (reg == null || !reg) return;

            String localSid = ch.attr(UserChannelRegistry.SESSION_ID_KEY).get();
            if (sessionId != null && !sessionId.isBlank()
                    && localSid != null && !localSid.equals(sessionId)) {
                return;
            }

            ch.eventLoop().execute(() -> ch.writeAndFlush(new TextWebSocketFrame(pushJson)));
        } else {
            // 跨节点转发必须携带 deviceId，否则对方只能再遍历
            SendMessageBo forwarded = new SendMessageBo(
                    sendMessageBo.getTargetUserId(),
                    sendMessageBo.getMessage(),
                    sendMessageBo.getChatType(),
                    sendMessageBo.getMessageType(),
                    sendMessageBo.getSenderId(),
                    sendMessageBo.getMessageId(),
                    sendMessageBo.getSendTime(),
                    deviceId,
                    sendMessageBo.getConversationId(),
                    sendMessageBo.getSeq()
            );

            // 转发到目标节点
            String routingKey = "websocket-message-" + node;
            rabbitTemplate.convertAndSend(routingKey + ".direct", routingKey, forwarded);
        }
    }
}