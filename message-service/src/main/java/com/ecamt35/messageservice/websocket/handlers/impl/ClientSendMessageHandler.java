package com.ecamt35.messageservice.websocket.handlers.impl;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.dto.CommonPacketDto;
import com.ecamt35.messageservice.model.entity.Message;
import com.ecamt35.messageservice.model.vo.MessageAckVo;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.service.MessageCommandService;
import com.ecamt35.messageservice.websocket.dispatch.WsContext;
import com.ecamt35.messageservice.websocket.handlers.PacketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClientSendMessageHandler implements PacketHandler {

    private final MessageCommandService commandService;

    @Override
    public int type() {
        return PacketTypeConstant.CLIENT_REQUEST_SENT;
    }

    @Override
    public void handle(WsContext ctx, CommonPacketDto packet) {
        Map<String, Object> data = packet.getData();

        String content = Convert.toStr(data.get("content"));
        Long clientMsgId = Convert.toLong(data.get("clientMsgId"));
        // 0=private,1=group
        Integer chatType = Convert.toInt(data.get("chatType"));
        Integer msgType = Convert.toInt(data.get("msgType"));

        Long receiverId = Convert.toLong(data.get("receiverId"));         // private 需要
        Long conversationId = Convert.toLong(data.get("conversationId")); // group 需要

        if (clientMsgId == null || chatType == null || msgType == null) {
            ctx.send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, data));
            return;
        }

        Long senderId = ctx.currentUserId();
        if (senderId == null) {
            ctx.send(new PushVo(PacketTypeConstant.INSUFFICIENT_PERMISSIONS, data));
            return;
        }

        // private
        if (chatType == 0 && receiverId == null) {
            ctx.send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, data));
            return;
        }
        // group
        if (chatType == 1 && conversationId == null) {
            ctx.send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, data));
            return;
        }

        Message msg;
        try {
            msg = commandService.sendMessage(
                    senderId, receiverId, conversationId, clientMsgId, chatType, msgType, content
            );
        } catch (IllegalStateException ex) {
            ctx.send(new PushVo(PacketTypeConstant.INSUFFICIENT_PERMISSIONS, ex.getMessage()));
            return;
        }

        MessageAckVo ack = new MessageAckVo();
        ack.setMessageId(msg.getId());
        ack.setSendTime(msg.getSendTime());
        ack.setClientMsgId(clientMsgId);

        ctx.send(new PushVo(PacketTypeConstant.SERVER_ACK_SENT, ack));
    }
}