package com.ecamt35.messageservice.websocket.dispatch;

import com.ecamt35.messageservice.model.dto.CommonPacketDto;

public interface MessageDispatcher {
    void dispatch(WsContext ctx, CommonPacketDto packet);
}