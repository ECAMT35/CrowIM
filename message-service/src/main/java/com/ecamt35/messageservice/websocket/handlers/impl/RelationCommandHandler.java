package com.ecamt35.messageservice.websocket.handlers.impl;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.constant.BusinessErrorCodeConstant;
import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.constant.ProtocolErrorCodeConstant;
import com.ecamt35.messageservice.model.dto.CommonPacketDto;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.model.vo.RelationAckVo;
import com.ecamt35.messageservice.service.RelationDomainService;
import com.ecamt35.messageservice.websocket.dispatch.WsContext;
import com.ecamt35.messageservice.websocket.handlers.PacketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class RelationCommandHandler implements PacketHandler {

    private final RelationDomainService relationDomainService;

    @Override
    public int type() {
        return PacketTypeConstant.CLIENT_RELATION_COMMAND;
    }

    @Override
    public void handle(WsContext ctx, CommonPacketDto packet) {
        Map<String, Object> data = packet.getData();
        String requestId = data == null ? null : Convert.toStr(data.get("requestId"));
        String op = data == null ? null : Convert.toStr(data.get("op"));

        Long userId = ctx.currentUserId();
        if (userId == null) {
            sendAck(ctx, requestId, op, ProtocolErrorCodeConstant.UNAUTHORIZED, "unauthorized", null);
            return;
        }

        if (data == null || data.isEmpty()) {
            sendAck(ctx, requestId, op, ProtocolErrorCodeConstant.BAD_REQUEST, "data is required", null);
            return;
        }

        if (requestId == null || requestId.isBlank() || op == null || op.isBlank()) {
            sendAck(ctx, requestId, op, ProtocolErrorCodeConstant.BAD_REQUEST, "requestId/op is required", null);
            return;
        }

        Map<String, Object> payload;
        Object payloadObj = data.get("payload");
        if (payloadObj == null) {
            payload = Collections.emptyMap();
        } else if (payloadObj instanceof Map<?, ?>) {
            payload = Convert.convert(Map.class, payloadObj);
        } else {
            sendAck(ctx, requestId, op, ProtocolErrorCodeConstant.BAD_REQUEST, "payload must be object", null);
            return;
        }

        try {
            Object result = relationDomainService.execute(userId, op, payload);
            sendAck(ctx, requestId, op, ProtocolErrorCodeConstant.SUCCESS, "ok", result);
        } catch (Exception ex) {
            log.error("Relation command failed, userId:{}, requestId:{}, op:{}", userId, requestId, op, ex);
            sendAck(ctx, requestId, op, BusinessErrorCodeConstant.BIZ_ERROR, ex.getMessage(), null);
        }
    }

    /**
     * 关系域统一应答，保证客户端可按 requestId 做幂等去重。
     */
    private void sendAck(WsContext ctx, String requestId, String op, int code, String message, Object data) {
        ctx.send(new PushVo(PacketTypeConstant.SERVER_RELATION_ACK,
                new RelationAckVo(requestId, op, code, message, data)
        ));
    }
}
