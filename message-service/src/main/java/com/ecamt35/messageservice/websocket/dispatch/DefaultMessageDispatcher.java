package com.ecamt35.messageservice.websocket.dispatch;

import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.dto.CommonPacketDto;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.websocket.handlers.PacketHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class DefaultMessageDispatcher implements MessageDispatcher {

    private final Map<Integer, PacketHandler> handlerMap;

    public DefaultMessageDispatcher(Map<Integer, PacketHandler> handlerMap) {
        this.handlerMap = handlerMap;
    }

    /**
     * 根据 packetType 分发到对应 PacketHandler 执行。
     *
     * @param ctx    WebSocket上下文
     * @param packet 报文通用格式包
     */
    @Override
    public void dispatch(WsContext ctx, CommonPacketDto packet) {
        if (packet == null || packet.getPacketType() == null) {
            ctx.send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, packet));
            return;
        }
        PacketHandler handler = handlerMap.get(packet.getPacketType());
        if (handler == null) {
            log.warn("No handler for packetType={}", packet.getPacketType());
            ctx.send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, packet));
            return;
        }
        handler.handle(ctx, packet);
    }
}