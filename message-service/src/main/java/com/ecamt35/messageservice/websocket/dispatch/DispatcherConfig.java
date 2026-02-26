package com.ecamt35.messageservice.websocket.dispatch;

import com.ecamt35.messageservice.websocket.handlers.PacketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class DispatcherConfig {

    /**
     * 策略工厂，收集所有策略实现，构建策略注册表
     */
    @Bean
    public MessageDispatcher messageDispatcher(List<PacketHandler> handlers) {
        Map<Integer, PacketHandler> map = handlers.stream()
                .collect(Collectors.toMap(PacketHandler::type, Function.identity()));
        return new DefaultMessageDispatcher(map);
    }
}