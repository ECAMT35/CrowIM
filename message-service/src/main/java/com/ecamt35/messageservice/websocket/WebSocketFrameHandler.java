package com.ecamt35.messageservice.websocket;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Snowflake;
import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.bo.SendMessageBo;
import com.ecamt35.messageservice.model.dto.CommonPacketDto;
import com.ecamt35.messageservice.model.entity.MessagePrivateChat;
import com.ecamt35.messageservice.model.vo.MessageAckVo;
import com.ecamt35.messageservice.model.vo.PushVo;
import com.ecamt35.messageservice.service.MessagePrivateChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;
import java.util.Set;

/**
 * WebSocket 消息处理器，负责处理客户端的 WebSocket 连接、消息接收和异常处理。
 */

@Slf4j
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    //客户端注册命令前缀，格式为 "REGISTER:<userId>"。
    private static final String REGISTER_CMD = "REGISTER:";
    private static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    private static final String REGISTRATION_REQUIRED = "ERROR:Please register first by sending REGISTER:<your-user-id>";
    private static final String WELCOME_MESSAGE = "Welcome! Please register by sending: REGISTER:<your-user-id>";
    // 所有依赖改为构造器注入
    private final MessageService messageService;
    private final Snowflake snowflake;
    private final MessagePrivateChatService messagePrivateChatService;
    private final ObjectMapper objectMapper;
    private final UserChannelRegistry userChannelRegistry;

    // 构造器注入，在初始化 Pipeline 时传入 Bean
    public WebSocketFrameHandler(MessageService messageService,
                                 Snowflake snowflake,
                                 MessagePrivateChatService messagePrivateChatService,
                                 ObjectMapper objectMapper,
                                 UserChannelRegistry userChannelRegistry) {
        this.messageService = messageService;
        this.snowflake = snowflake;
        this.messagePrivateChatService = messagePrivateChatService;
        this.objectMapper = objectMapper;
        this.userChannelRegistry = userChannelRegistry;
    }

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
            PushVo errorVo = new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, message);
            try {
                String pushVo = objectMapper.writeValueAsString(errorVo);
                ctx.writeAndFlush(new TextWebSocketFrame(pushVo));
            } catch (JsonProcessingException ex) {
                log.info("Invalid message format: {}", message, ex);
            }
            return;
        }

        Map<String, Object> data = commonPacketDto.getData();
        String deviceId = Convert.toStr(data.get("deviceId"));
        Long userId = Convert.toLong(data.get("userId"));

        userChannelRegistry.registerUser(userId, deviceId, ctx.channel());
        ctx.writeAndFlush(new TextWebSocketFrame(REGISTER_SUCCESS));

    }

    /**
     * 处理用户间消息。
     *
     * @param ctx     ChannelHandlerContext，连接上下文
     * @param message 接收到的消息内容
     */
    private void handleUserMessage(ChannelHandlerContext ctx, String message) {
        //已注册，所以接下来的内容是json
        CommonPacketDto commonPacketDto;
        try {
            commonPacketDto = objectMapper.readValue(message, CommonPacketDto.class);
        } catch (JsonProcessingException e) {
            log.info("Invalid JSON format: {}", message, e);
            PushVo errorVo = new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, message);
            try {
                String pushVo = objectMapper.writeValueAsString(errorVo);
                ctx.writeAndFlush(new TextWebSocketFrame(pushVo));
            } catch (JsonProcessingException ex) {
                log.info("Invalid message format: {}", message, ex);
            }
            return;
        }

        Integer packetType = commonPacketDto.getPacketType();
        if (packetType == null) {
            PushVo pushVo = new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, message);
            try {
                String json = objectMapper.writeValueAsString(pushVo);
                ctx.writeAndFlush(new TextWebSocketFrame(json));
            } catch (JsonProcessingException e) {
                log.info("Invalid message format: {}", message, e);
            }
            return;
        }

        ChannelId channelId = ctx.channel().id();
        Thread.ofVirtual()
                .start(() -> {
                    log.info("Handling message in thread: {}", Thread.currentThread());
                    dispatchMessageTask(channelId, commonPacketDto);
                });
    }

    /**
     * 消息处理分发
     */
    private void dispatchMessageTask(ChannelId channelId, CommonPacketDto commonPacketDto) {
        Channel channel = messageService.getChannel(channelId);
        if (channel == null) {
            log.warn("Channel not found, channelId:{}", channelId);
            return;
        }

        Integer packetType = commonPacketDto.getPacketType();
        Map<String, Object> data = commonPacketDto.getData();

        switch (packetType) {
            case PacketTypeConstant.CLIENT_REQUEST_SENT -> handleClientSendMessage(channel, data);
            case PacketTypeConstant.CLIENT_ACK_READ -> handleClientAckRead(channel, data);
            default -> {
                log.warn("Unsupported message type: {}", packetType);
                sendThreadSafeResponse(channel, new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, commonPacketDto));
            }
        }
    }


    /**
     * Msg:R
     * Msg:N
     * Msg:A
     * 处理客户端发送消息请求
     */
    private void handleClientSendMessage(Channel channel, Map<String, Object> data) {

        String content = Convert.toStr(data.get("content"));
        Long clientMsgId = Convert.toLong(data.get("clientMsgId"));
        Integer chatType = Convert.toInt(data.get("chatType"));
        Integer messageType = Convert.toInt(data.get("msgType"));
        Long receiverId = Convert.toLong(data.get("receiverId"));
        String deviceId = Convert.toStr(data.get("deviceId"));

        // 参数校验
        if (chatType == null || messageType == null || receiverId == null || clientMsgId == null) {
            sendThreadSafeResponse(channel, new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, data));
            log.info("Invalid message format: {}", data);
            return;
        }

        Long senderId = messageService.getUserId(channel);
        if (senderId == null) {
            sendThreadSafeResponse(channel, new PushVo(PacketTypeConstant.INSUFFICIENT_PERMISSIONS, data));
            log.info("User not logged in, channel: {}", channel.id());
            return;
        }

        // 以下均为单聊的处理，群聊判断chatType=0其他处理
        // todo


        // 单聊

        Long sendTime = System.currentTimeMillis();

        // DB持久化
        long id = snowflake.nextId();
        MessagePrivateChat messagePrivateChat = new MessagePrivateChat(
                id, clientMsgId, senderId, receiverId, content, messageType, 1, sendTime, null, null, null
        );

        boolean isFirstTimeSave = false;
        try {
            messagePrivateChatService.save(messagePrivateChat);
            isFirstTimeSave = true;
            log.info("Message persisted successfully, clientMsgId:{}, id:{}", clientMsgId, id);
        } catch (DuplicateKeyException e) {
            // clientMsgId唯一键冲突, 客户端重复请求
            MessagePrivateChat chat = messagePrivateChatService.findByClientMsgId(clientMsgId, senderId);
            if (chat == null) {
                log.error("Duplicate message but permission denied, clientMsgId:{}", clientMsgId);
                sendThreadSafeResponse(channel, new PushVo(PacketTypeConstant.INSUFFICIENT_PERMISSIONS, null));
                return;
            }
            id = chat.getId();
            sendTime = chat.getSendTime();
            log.warn("Duplicate client message detected, ignore insertion, clientMsgId:{}", clientMsgId);
        }

        // Msg:N, MQ消息推送
        if (isFirstTimeSave) {
            SendMessageBo sendMessageBo = new SendMessageBo(
                    receiverId, content, chatType, messageType, senderId, id, sendTime, deviceId
            );
            messageService.sendMessageToUser(sendMessageBo);
        }

        // Msg:A, 注意线程安全方式
        MessageAckVo messageAckVo = new MessageAckVo();
        messageAckVo.setMessageId(id);
        messageAckVo.setSendTime(sendTime);
        messageAckVo.setClientMsgId(clientMsgId);

        PushVo pushVo = new PushVo(PacketTypeConstant.SERVER_ACK_SENT, messageAckVo);
        sendThreadSafeResponse(channel, pushVo);
    }

    /**
     * 切回Netty IO线程执行writeAndFlush, 保证线程安全的响应推送
     */
    private void sendThreadSafeResponse(Channel channel, PushVo pushVo) {

        if (channel == null || !channel.isActive() || !channel.isWritable()) {
            log.warn("Channel is inactive, skip sending response");
            return;
        }

        String pushVoJson;
        try {
            pushVoJson = objectMapper.writeValueAsString(pushVo);
        } catch (JsonProcessingException e) {
            log.warn("Invalid JSON format: {}", pushVo, e);
            return;
        }

        // 切回Netty IO线程，保证writeAndFlush线程安全
        channel.eventLoop().execute(() -> channel.writeAndFlush(new TextWebSocketFrame(pushVoJson)));
    }

    /**
     * Ack:R
     * Ack:A
     * 隐式Ack:N
     */
    private void handleClientAckRead(Channel channel, Map<String, Object> data) {

        Set<Long> ids = Convert.toSet(Long.class, data.get("ids"));
        Long receiverId = Convert.toLong(data.get("receiverId"));

        if (ids == null || ids.isEmpty() || receiverId == null) {
            sendThreadSafeResponse(channel, new PushVo(PacketTypeConstant.INVALID_MESSAGE_FORMAT, data));
            log.info("Invalid message format: {}", data);
            return;
        }

        // 更新DB状态
        Long currentUserId = messageService.getUserId(channel);
        if (!receiverId.equals(currentUserId)) {
            sendThreadSafeResponse(channel, new PushVo(PacketTypeConstant.INSUFFICIENT_PERMISSIONS, data));
            log.info("Insufficient permissions to update, user:{} try to update msg:{}", currentUserId, data);
            return;
        }
        int i = messagePrivateChatService.updateStatusToReadBatch(ids, currentUserId);
        log.info("更新已读状态数目: {}, messageId: {}", i, ids);

        PushVo pushVo = new PushVo(PacketTypeConstant.SERVER_ACK_READ, ids);
        sendThreadSafeResponse(channel, pushVo);
    }

}