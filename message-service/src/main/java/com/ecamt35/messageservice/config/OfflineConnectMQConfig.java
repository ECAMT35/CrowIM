package com.ecamt35.messageservice.config;

import com.ecamt35.messageservice.constant.OfflineConnectConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OfflineConnectMQConfig {

    private final OfflineConnectConstant offlineConnectConstant;

    public OfflineConnectMQConfig(OfflineConnectConstant offlineConnectConstant) {
        this.offlineConnectConstant = offlineConnectConstant;
    }

    @Bean
    public DirectExchange offlineConnectExchange() {
        return new DirectExchange(
                offlineConnectConstant.getOfflineConnectExchange(),
                true,   // durable
                false   // autoDelete
        );
    }

    @Bean
    public Queue offlineConnectQueue() {
        // 下线队列不设死信（或可按需设置），简单持久化即可
        return QueueBuilder.durable(offlineConnectConstant.getOfflineConnectQueue()).build();
    }

    @Bean
    public Binding offlineConnectBinding() {
        return BindingBuilder.bind(offlineConnectQueue())
                .to(offlineConnectExchange())
                .with(offlineConnectConstant.getOfflineConnectRoutingKey());
    }
}
