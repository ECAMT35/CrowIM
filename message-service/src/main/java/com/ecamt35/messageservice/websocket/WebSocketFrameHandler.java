package com.ecamt35.messageservice.websocket;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.constant.BusinessErrorCodeConstant;
import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.constant.ProtocolErrorCodeConstant;
import com.ecamt35.messageservice.model.dto.CommonPacketDto;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.model.vo.RegisterAckVo;
import com.ecamt35.messageservice.model.vo.RegisterNackVo;
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

    private final ObjectMapper objectMapper;
    private final UserChannelRegistry userChannelRegistry;
    private final ExecutorService virtualExecutor;
    private final MessageDispatcher dispatcher;
    private final String nodeName;

    public WebSocketFrameHandler(ObjectMapper objectMapper,
                                 UserChannelRegistry userChannelRegistry,
                                 ExecutorService virtualExecutor,
                                 MessageDispatcher dispatcher,
                                 String nodeName) {
        this.objectMapper = objectMapper;
        this.userChannelRegistry = userChannelRegistry;
        this.virtualExecutor = virtualExecutor;
        this.dispatcher = dispatcher;
        this.nodeName = nodeName;
    }

    /**
     * 当新连接加入 pipeline 时触发，初始化注册状态。
     *
     * @param ctx ChannelHandlerContext，包含连接上下文信息
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.info("New connection from: {}", ctx.channel().remoteAddress());
    }

    /**
     * 处理 WebSocket 生命周期事件。
     * 在握手完成后立即回写当前节点名，便于客户端感知所连接节点。
     *
     * @param ctx ChannelHandlerContext，连接上下文
     * @param evt 事件对象
     * @throws Exception 异常
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            Channel ch = ctx.channel();
            ch.eventLoop().execute(() -> {
                if (ch.isActive()) {
                    ch.writeAndFlush(new TextWebSocketFrame(nodeName));
                }
            });
            return;
        }
        super.userEventTriggered(ctx, evt);
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
            sendRegisterNack(
                    ctx.channel(),
                    null,
                    null,
                    null,
                    ProtocolErrorCodeConstant.BAD_REQUEST,
                    "invalid register payload",
                    "invalid json format"
            );
            return;
        }

        Map<String, Object> data = commonPacketDto.getData();
        String requestId = data == null ? null : Convert.toStr(data.get("requestId"));
        if (data == null || data.isEmpty()) {
            sendRegisterNack(
                    ctx.channel(),
                    requestId,
                    null,
                    null,
                    ProtocolErrorCodeConstant.BAD_REQUEST,
                    "invalid register payload",
                    "data is required"
            );
            ctx.close();
            return;
        }
        String deviceId = Convert.toStr(data.get("deviceId"));
        Long userId = Convert.toLong(data.get("userId"));

        if (userId == null || deviceId == null || deviceId.isBlank()) {
            sendRegisterNack(
                    ctx.channel(),
                    requestId,
                    userId,
                    deviceId,
                    ProtocolErrorCodeConstant.BAD_REQUEST,
                    "invalid register payload",
                    "userId/deviceId is required"
            );
            ctx.close();
            return;
        }

        Channel ch = ctx.channel();
        userChannelRegistry.registerUserAsync(userId, deviceId, ch)
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        log.warn("Register failed, userId={}, deviceId={}, reason={}", userId, deviceId, ex.getMessage());
                        ch.eventLoop().execute(() -> {
                            sendRegisterNack(
                                    ch,
                                    requestId,
                                    userId,
                                    deviceId,
                                    BusinessErrorCodeConstant.BIZ_ERROR,
                                    "register failed",
                                    ex.getMessage()
                            );
                            ch.close();
                        });
                        return;
                    }
                    sendRegisterAck(ch, requestId, userId, deviceId);
                });
    }

    /**
     * 发送注册成功应答，返回用户与节点信息供客户端建立会话上下文。
     * 参数：
     *
     * @param channel   当前连接通道
     * @param requestId 客户端请求 ID，可为空
     * @param userId    用户 ID
     * @param deviceId  设备 ID
     */
    private void sendRegisterAck(Channel channel, String requestId, Long userId, String deviceId) {
        new WsContext(channel, objectMapper, userChannelRegistry)
                .send(new PushVo(
                        PacketTypeConstant.SERVER_REGISTER_ACK,
                        new RegisterAckVo(
                                requestId,
                                userId,
                                deviceId,
                                nodeName,
                                System.currentTimeMillis(),
                                ProtocolErrorCodeConstant.SUCCESS,
                                "register success"
                        )
                ));
    }

    /**
     * 发送注册失败应答，返回错误码和失败原因供客户端重试与排障。
     * 参数：
     *
     * @param channel     当前连接通道
     * @param requestId   客户端请求 ID，可为空
     * @param userId      用户 ID，可为空
     * @param deviceId    设备 ID，可为空
     * @param code        协议/业务错误码
     * @param message     错误摘要
     * @param errorDetail 错误细节
     */
    private void sendRegisterNack(Channel channel,
                                  String requestId,
                                  Long userId,
                                  String deviceId,
                                  Integer code,
                                  String message,
                                  String errorDetail) {
        new WsContext(channel, objectMapper, userChannelRegistry)
                .send(new PushVo(
                        PacketTypeConstant.SERVER_REGISTER_NACK,
                        new RegisterNackVo(
                                requestId,
                                userId,
                                deviceId,
                                nodeName,
                                System.currentTimeMillis(),
                                code,
                                message,
                                errorDetail
                        )
                ));
    }
}
