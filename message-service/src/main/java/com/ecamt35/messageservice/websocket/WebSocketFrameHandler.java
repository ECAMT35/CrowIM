package com.ecamt35.messageservice.websocket;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.dto.CommonPacketDto;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.websocket.dispatch.MessageDispatcher;
import com.ecamt35.messageservice.websocket.dispatch.WsContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * WebSocket 消息处理器，负责处理客户端的 WebSocket 连接、消息接收和异常处理。
 */
@Slf4j
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    private static final String WELCOME_MESSAGE = "Welcome! Please register with {userId, deviceId}";

    private final ObjectMapper objectMapper;
    private final UserChannelRegistry userChannelRegistry;
    private final ExecutorService virtualExecutor;

    private final MessageDispatcher dispatcher;

    public WebSocketFrameHandler(ObjectMapper objectMapper,
                                 UserChannelRegistry userChannelRegistry,
                                 ExecutorService virtualExecutor,
                                 MessageDispatcher dispatcher) {
        this.objectMapper = objectMapper;
        this.userChannelRegistry = userChannelRegistry;
        this.virtualExecutor = virtualExecutor;
        this.dispatcher = dispatcher;
    }

    /**
     * 当新连接建立时触发，发送欢迎信息并初始化连接状态。
     * 设置连接的注册状态为未注册，并向客户端发送响应报文。
     *
     * @param ctx ChannelHandlerContext，包含连接上下文信息
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctx.channel().attr(UserChannelRegistry.REGISTERED_KEY).set(false);
        ctx.writeAndFlush(new TextWebSocketFrame(WELCOME_MESSAGE));
        log.info("New connection from: {}", ctx.channel().remoteAddress());
    }

    /**
     * 当连接关闭时触发，执行用户注销逻辑，移除用户与通道的绑定关系。
     *
     * @param ctx ChannelHandlerContext，包含连接上下文信息
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        userChannelRegistry.unregisterAsync(ctx.channel());
        log.info("Connection closed: {}", ctx.channel().remoteAddress());
    }

    /**
     * 异常处理方法，记录错误日志并关闭异常连接。
     *
     * @param ctx   ChannelHandlerContext，连接上下文
     * @param cause 异常对象
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket error from {}: {}", ctx.channel().remoteAddress(), cause.getMessage());
        ctx.close();
    }

    /**
     * 处理接收到的 WebSocket 帧消息。
     * 根据连接是否已注册，分别处理注册请求或用户间消息。
     *
     * @param ctx   ChannelHandlerContext，连接上下文
     * @param frame WebSocketFrame，接收到的消息帧
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        // Ping帧，自动回复Pong
        if (frame instanceof PingWebSocketFrame pingFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(pingFrame.content().retain()));
            return;
        }
        // 处理关闭帧
        if (frame instanceof CloseWebSocketFrame closeFrame) {
            ctx.writeAndFlush(closeFrame.retain()).addListener(future -> ctx.close());
            return;
        }
        // 非文本帧直接忽略
        if (!(frame instanceof TextWebSocketFrame)) {
            return;
        }

        String text = ((TextWebSocketFrame) frame).text();
        Boolean isRegistered = ctx.channel().attr(UserChannelRegistry.REGISTERED_KEY).get();

        // 未注册用户处理
        if (isRegistered == null || !isRegistered) {
            handleRegistration(ctx, text);
            return;
        }

        // 已注册用户处理消息转发
        final CommonPacketDto packet;
        try {
            packet = objectMapper.readValue(text, CommonPacketDto.class);
        } catch (JsonProcessingException e) {
            new WsContext(ctx.channel(), objectMapper, userChannelRegistry)
                    .send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, text));
            return;
        }

        final Channel ch = ctx.channel();
        virtualExecutor.execute(() -> {
            WsContext wsCtx = new WsContext(ch, objectMapper, userChannelRegistry);
            dispatcher.dispatch(wsCtx, packet);
        });
    }

    /**
     * 处理用户注册请求。
     *
     * @param ctx     ChannelHandlerContext，连接上下文
     * @param message 接收到的消息内容
     */
    private void handleRegistration(ChannelHandlerContext ctx, String message) {
        CommonPacketDto commonPacketDto;
        try {
            commonPacketDto = objectMapper.readValue(message, CommonPacketDto.class);
        } catch (JsonProcessingException e) {
            log.info("Invalid JSON format: {}", message, e);
            new WsContext(ctx.channel(), objectMapper, userChannelRegistry)
                    .send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, message));
            return;
        }

        Map<String, Object> data = commonPacketDto.getData();
        if (data == null || data.isEmpty()) {
            new WsContext(ctx.channel(), objectMapper, userChannelRegistry)
                    .send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, message));
            ctx.close();
            return;
        }
        String deviceId = Convert.toStr(data.get("deviceId"));
        Long userId = Convert.toLong(data.get("userId"));

        if (userId == null || deviceId == null || deviceId.isBlank()) {
            new WsContext(ctx.channel(), objectMapper, userChannelRegistry)
                    .send(new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, message));
            ctx.close();
            return;
        }

        Channel ch = ctx.channel();
        userChannelRegistry.registerUserAsync(userId, deviceId, ch)
                .whenComplete((v, ex) -> {
                    if (ex == null) {
                        ch.eventLoop().execute(() -> {
                            if (ch.isActive()) {
                                ch.writeAndFlush(new TextWebSocketFrame(REGISTER_SUCCESS));
                            }
                        });
                    } else {
                        log.warn("Register failed, userId={}, deviceId={}, reason={}", userId, deviceId, ex.getMessage());
                        ch.eventLoop().execute(() -> {
                            if (ch.isActive()) {
                                ch.writeAndFlush(new TextWebSocketFrame("REGISTER_FAILED"));
                                ch.close();
                            }
                        });
                    }
                });
    }
}