package com.ecamt35.messageservice.websocket;

import com.ecamt35.messageservice.model.bo.SendMessageBo;
import com.ecamt35.messageservice.model.dto.ImMessageDto;
import com.ecamt35.messageservice.model.vo.MessageAckVo;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * WebSocket 消息处理器，负责处理客户端的 WebSocket 连接、消息接收和异常处理。
 */
@Component
@Scope("prototype") // 每个连接独立实例
@Slf4j
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    @Resource
    private MessageService messageService;

    // Jackson的对象映射器，用于JSON解析
    private static final ObjectMapper objectMapper = new ObjectMapper();

    //客户端注册命令前缀，格式为 "REGISTER:<userId>"。
    private static final String REGISTER_CMD = "REGISTER:";
    private static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    private static final String MESSAGE_FORMAT_ERROR = "ERROR:Invalid message format. Use 'targetUserId:message'";
    private static final String REGISTRATION_REQUIRED = "ERROR:Please register first by sending REGISTER:<your-user-id>";
    private static final String WELCOME_MESSAGE = "Welcome! Please register by sending: REGISTER:<your-user-id>";

    /**
     * 当新连接建立时触发，发送欢迎信息并初始化连接状态。
     * 设置连接的注册状态为未注册，并向客户端发送欢迎消息。
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
        messageService.unregisterUser(ctx.channel());
        log.info("Connection closed: {}", ctx.channel().remoteAddress());
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
        if (!(frame instanceof TextWebSocketFrame)) {
            return; // 非文本帧直接忽略
        }

        String message = ((TextWebSocketFrame) frame).text();
        Boolean isRegistered = ctx.channel().attr(UserChannelRegistry.REGISTERED_KEY).get();

        if (isRegistered == null || !isRegistered) {
            handleRegistration(ctx, message); // 未注册用户处理
        } else {
            handleUserMessage(ctx, message); // 已注册用户处理消息转发
        }
    }

    /**
     * 处理用户注册请求。
     * 解析 "REGISTER:<userId>" 格式的消息，验证用户 ID 并完成注册。
     *
     * @param ctx     ChannelHandlerContext，连接上下文
     * @param message 接收到的消息内容
     */
    private void handleRegistration(ChannelHandlerContext ctx, String message) {
        if (message.startsWith(REGISTER_CMD)) {
            try {
                long userId = Long.parseLong(message.substring(REGISTER_CMD.length()).trim());
                messageService.registerUser(userId, ctx.channel()); // 调用业务服务注册用户
                ctx.writeAndFlush(new TextWebSocketFrame(REGISTER_SUCCESS)); // 返回注册成功
            } catch (NumberFormatException e) {
                log.warn("Invalid user ID format: {}", message);
                ctx.writeAndFlush(new TextWebSocketFrame("ERROR:Invalid user ID format"));
            }
        } else {
            ctx.writeAndFlush(new TextWebSocketFrame(REGISTRATION_REQUIRED)); // 提示用户先注册
        }
    }

    /**
     * 处理用户间消息。
     * 消息格式需为 "targetUserId:message"，解析目标用户 ID 并转发消息。
     *
     * @param ctx     ChannelHandlerContext，连接上下文
     * @param message 接收到的消息内容
     */
    private void handleUserMessage(ChannelHandlerContext ctx, String message) {
        //已注册，所以接下来的内容是json
        ImMessageDto imMessageDto;
        try {
            imMessageDto = objectMapper.readValue(message, ImMessageDto.class);
        } catch (JsonProcessingException e) {
            ctx.writeAndFlush(new TextWebSocketFrame(MESSAGE_FORMAT_ERROR));
            log.error("Invalid message format: {}", message);
            throw new RuntimeException(e);
        }

        Long receiverId = imMessageDto.getReceiverId();
        String content = imMessageDto.getContent();
        Integer messageType = imMessageDto.getMessage_type();
        Long senderId = messageService.getUserId(ctx.channel());
        Long messageId = imMessageDto.getMessageId();

        SendMessageBo sendMessageBo = new SendMessageBo();
        sendMessageBo.setSenderId(senderId);
        sendMessageBo.setReceiverId(receiverId);
        sendMessageBo.setMessage(content);
        sendMessageBo.setMessageType(messageType);
        sendMessageBo.setMessageId(messageId);
        Long sendTime = System.currentTimeMillis();
        sendMessageBo.setSendTime(sendTime);

        // Msg:N
        messageService.sendMessageToMQ(sendMessageBo);

        // Msg:A
        MessageAckVo messageAckVo = new MessageAckVo();
        messageAckVo.setMessageId(messageId);
        messageAckVo.setSendTime(sendTime);

        PushVo pushVo = new PushVo(2, messageAckVo);

        String pushVoJson;
        try {
            pushVoJson = objectMapper.writeValueAsString(pushVo);
        } catch (JsonProcessingException e) {
            ctx.writeAndFlush(new TextWebSocketFrame(MESSAGE_FORMAT_ERROR));
            log.error("Invalid message format: {}", pushVo);
            throw new RuntimeException(e);
        }
        ctx.writeAndFlush(new TextWebSocketFrame(pushVoJson));
    }

    /**
     * 异常处理方法，记录错误日志并关闭异常连接。
     *
     * @param ctx   ChannelHandlerContext，连接上下文
     * @param cause 异常对象
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket error from {}: {}",
                ctx.channel().remoteAddress(), cause.getMessage());
        ctx.close();
    }
}