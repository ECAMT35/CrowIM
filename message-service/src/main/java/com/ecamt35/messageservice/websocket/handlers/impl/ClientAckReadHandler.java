package com.ecamt35.messageservice.websocket.handlers.impl;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.dto.CommonPacketDto;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.service.CursorService;
import com.ecamt35.messageservice.websocket.dispatch.WsContext;
import com.ecamt35.messageservice.websocket.handlers.PacketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClientAckReadHandler implements PacketHandler {

    private final CursorService cursorService;

    @Override
    public int type() {
        return PacketTypeConstant.CLIENT_ACK_READ;
    }

    /**
     * 推进 read 游标，落库 member
     *
     * @param ctx    WebSocket上下文
     * @param packet 报文通用格式包
     */
    @Override
    public void handle(WsContext ctx, CommonPacketDto packet) {
        Map<String, Object> data = packet.getData();

        Long convId = Convert.toLong(data.get("conversationId"));
        Long readSeq = Convert.toLong(data.get("readSeq"));

        Long userId = ctx.currentUserId();
        if (userId == null || convId == null || readSeq == null) {
            ctx.send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, data));
            return;
        }

        // Redis，DB幂等推进确保不倒退
        long finalRead = cursorService.advanceRead(userId, convId, readSeq);

        Map<String, Object> ack = new HashMap<>();
        ack.put("conversationId", convId);
        ack.put("readSeq", finalRead);

        ctx.send(new PushVo(PacketTypeConstant.SERVER_ACK_READ, ack));
    }
}