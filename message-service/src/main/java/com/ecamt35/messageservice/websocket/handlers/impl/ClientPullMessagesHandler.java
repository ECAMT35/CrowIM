package com.ecamt35.messageservice.websocket.handlers.impl;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.dto.CommonPacketDto;
import com.ecamt35.messageservice.model.vo.PullMessagesRespVo;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.service.MessageService;
import com.ecamt35.messageservice.websocket.dispatch.WsContext;
import com.ecamt35.messageservice.websocket.handlers.PacketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClientPullMessagesHandler implements PacketHandler {

    private final MessageService messageService;

    @Override
    public int type() {
        return PacketTypeConstant.CLIENT_PULL_MESSAGES;
    }

    @Override
    public void handle(WsContext ctx, CommonPacketDto packet) {

        Long userId = ctx.currentUserId();
        if (userId == null) {
            ctx.send(new PushVo(PacketTypeConstant.INSUFFICIENT_PERMISSIONS, null));
            return;
        }

        Map<String, Object> data = packet.getData();
        if (data == null || data.isEmpty()) {
            ctx.send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, null));
            return;
        }

        Long convId = Convert.toLong(data.get("conversationId"));
        // ReadSeq≠AfterSeq
        // AfterSeq:客户端本地已拥有/已同步到哪的同步游标，多端情况下肯定会出现ReadSeq>AfterSeq
        Long afterSeq = Convert.toLong(data.get("afterSeq"));
        Integer limit = Convert.toInt(data.get("limit"));
        // 客户端把 upperBoundSeq 设置为服务端上一次返回的值
        Long upperBoundSeq = Convert.toLong(data.get("upperBoundSeq"));

        if (convId == null) {
            ctx.send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, data));
            return;
        }

        PullMessagesRespVo resp = messageService.pullMessages(
                userId, convId, afterSeq, limit, upperBoundSeq
        );

        if (resp == null) {
            ctx.send(new PushVo(PacketTypeConstant.INSUFFICIENT_PERMISSIONS, data));
            return;
        }

        ctx.send(new PushVo(PacketTypeConstant.SERVER_MESSAGES, resp));
    }
}