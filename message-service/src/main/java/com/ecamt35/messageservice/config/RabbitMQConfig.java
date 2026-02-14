package com.ecamt35.messageservice.config;

import com.ecamt35.messageservice.constant.RabbitMQConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    private final RabbitMQConstant rabbitMQConstant;
    public RabbitMQConfig(RabbitMQConstant rabbitMQConstant) {
        this.rabbitMQConstant = rabbitMQConstant;
    }

    @Bean
    DirectExchange websocketMessageExchange() {
        return new DirectExchange(
                rabbitMQConstant.getWebsocketMessageExchange(),
                true,   // durable
                false   // autoDelete
        );
    }

    @Bean
    Queue websocketMessageQueue() {
        return QueueBuilder.durable(rabbitMQConstant.getWebsocketMessageQueue())
                .deadLetterExchange(rabbitMQConstant.getDeadWebsocketMessageExchange())
                .deadLetterRoutingKey(rabbitMQConstant.getDeadWebsocketMessageRoutingKey())
                .build();
    }

    @Bean
    Binding websocketBinding() {
        return BindingBuilder.bind(websocketMessageQueue())
                .to(websocketMessageExchange())
                .with(rabbitMQConstant.getWebsocketMessageKey());
    }


    @Bean
    DirectExchange deadWebsocketExchange() {
        return new DirectExchange(
                rabbitMQConstant.getDeadWebsocketMessageExchange(),
                true,
                false
        );
    }

    @Bean
    Queue deadWebsocketQueue() {
        return new Queue(rabbitMQConstant.getDeadWebsocketMessageQueue(), true);
    }

    @Bean
    Binding deadWebsocketBinding() {
        return BindingBuilder.bind(deadWebsocketQueue())
                .to(deadWebsocketExchange())
                .with(rabbitMQConstant.getDeadWebsocketMessageRoutingKey());
    }


    // 消息转换器
    @Bean
    public MessageConverter messageConverter() {
        // 1.定义消息转换器
        Jackson2JsonMessageConverter jackson2JsonMessageConverter = new Jackson2JsonMessageConverter();
        // 2.配置自动创建消息id，用于识别不同消息，也可以在业务中基于ID判断是否是重复消息
        //jackson2JsonMessageConverter.setCreateMessageIds(true);
        return jackson2JsonMessageConverter;
    }
}