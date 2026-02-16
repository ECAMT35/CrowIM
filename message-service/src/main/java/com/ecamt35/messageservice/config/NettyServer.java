package com.ecamt35.messageservice.config;

import cn.hutool.core.lang.Snowflake;
import com.ecamt35.messageservice.service.MessagePrivateChatService;
import com.ecamt35.messageservice.websocket.MessageService;
import com.ecamt35.messageservice.websocket.UserChannelRegistry;
import com.ecamt35.messageservice.websocket.WebSocketFrameHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class NettyServer {

    @Value("${netty.websocket.port:8080}")
    private int port;

    @Value("${netty.websocket.path:/ws}")
    private String path;

    @Value("${netty.websocket.max-frame-size:65536}")
    private int maxFrameSize;

    @Value("${netty.websocket.idle-timeout:60}")
    private int idleTimeout;

    @Resource
    private MessageService messageService;
    @Resource
    private Snowflake snowflake;
    @Resource
    private MessagePrivateChatService messagePrivateChatService;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private UserChannelRegistry userChannelRegistry;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    /**
     * 初始化 Netty 服务器
     */
    @PostConstruct
    public void start() {
        try {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024) // 队列大小
                    .option(ChannelOption.SO_REUSEADDR, true) // 多次绑定
                    .childOption(ChannelOption.SO_KEEPALIVE, true) // TCP keepalive
                    .childOption(ChannelOption.TCP_NODELAY, true) // 禁用 Nagle 算法
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // HTTP 编解码
                            pipeline.addLast(new HttpServerCodec());
                            // 聚合 HTTP 请求
                            pipeline.addLast(new HttpObjectAggregator(maxFrameSize));
                            // WebSocket 协议升级
                            pipeline.addLast(new WebSocketServerProtocolHandler(
                                    path, null, true, maxFrameSize, false, true
                            ));
                            // 空闲检测（仅读空闲）
                            pipeline.addLast(new IdleStateHandler(
                                    idleTimeout, 0, 0, TimeUnit.SECONDS
                            ));
                            // WebSocket 消息处理器
                            pipeline.addLast(
                                    new WebSocketFrameHandler(
                                            messageService,
                                            snowflake,
                                            messagePrivateChatService,
                                            objectMapper,
                                            userChannelRegistry
                                    )
                            );
                        }
                    });

            // 绑定端口并启动
            serverChannel = bootstrap.bind(port).sync().channel();
            log.info("Netty WebSocket server started at ws://localhost:{}{}", port, path);

        } catch (InterruptedException e) {
            log.error("Netty server start interrupted", e);
            stop();
        } catch (Exception e) {
            log.error("Failed to start Netty server", e);
            stop();
        }
    }

    /**
     * 停止 Netty 服务器
     */
    @PreDestroy
    public void stop() {
        log.info("Shutting down Netty WebSocket server...");

        if (serverChannel != null) {
            serverChannel.close();
        }

        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }

        log.info("Netty WebSocket server stopped.");
    }
}