package com.ecamt35.messageservice.websocket.handlers;

import com.ecamt35.messageservice.model.dto.CommonPacketDto;
import com.ecamt35.messageservice.websocket.dispatch.WsContext;

public interface PacketHandler {
    int type();

    void handle(WsContext ctx, CommonPacketDto packet);
}