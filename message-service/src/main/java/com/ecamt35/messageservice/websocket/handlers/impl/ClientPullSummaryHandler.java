package com.ecamt35.messageservice.websocket.handlers.impl;

import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.dto.CommonPacketDto;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.service.SummaryService;
import com.ecamt35.messageservice.websocket.dispatch.WsContext;
import com.ecamt35.messageservice.websocket.handlers.PacketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientPullSummaryHandler implements PacketHandler {

    private final SummaryService summaryService;

    @Override
    public int type() {
        return PacketTypeConstant.CLIENT_PULL_SUMMARY;
    }

    /**
     * 客户端请求才推
     *
     * @param ctx    WebSocket上下文
     * @param packet 报文通用格式包
     */
    @Override
    public void handle(WsContext ctx, CommonPacketDto packet) {
        Long userId = ctx.currentUserId();
        if (userId == null) {
            ctx.send(new PushVo(PacketTypeConstant.INSUFFICIENT_PERMISSIONS, null));
            return;
        }
        ctx.send(new PushVo(PacketTypeConstant.SERVER_SUMMARY, summaryService.buildSummary(userId)));
    }
}