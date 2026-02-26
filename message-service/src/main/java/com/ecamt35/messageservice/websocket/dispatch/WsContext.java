package com.ecamt35.messageservice.websocket.dispatch;

import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.websocket.UserChannelRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class WsContext {

    public final Channel channel;
    public final ObjectMapper objectMapper;
    public final UserChannelRegistry userChannelRegistry;

    /**
     * 线程安全回包
     *
     * @param pushVo（含 packetType/data）
     */
    public void send(PushVo pushVo) {
        if (channel == null || !channel.isActive() || !channel.isWritable()) {
            log.warn("Channel inactive, skip send");
            return;
        }
        final String json;
        try {
            json = objectMapper.writeValueAsString(pushVo);
        } catch (JsonProcessingException e) {
            log.warn("Serialize pushVo failed: {}", pushVo, e);
            return;
        }

        if (channel.eventLoop().inEventLoop()) {
            // 当前是 Channel 专属的 IO 线程，直接发送
            channel.writeAndFlush(new TextWebSocketFrame(json));
        } else {
            // 当前不是，提交到 EventLoop
            channel.eventLoop().execute(() ->
                    channel.writeAndFlush(new TextWebSocketFrame(json))
            );
        }
    }

    public Long currentUserId() {
        return userChannelRegistry.getUserId(channel);
    }
}