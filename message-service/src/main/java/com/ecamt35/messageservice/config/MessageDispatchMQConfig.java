package com.ecamt35.messageservice.config;

import com.ecamt35.messageservice.constant.MessageDispatchConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageDispatchMQConfig {

    private final MessageDispatchConstant messageDispatchConstant;

    public MessageDispatchMQConfig(MessageDispatchConstant messageDispatchConstant) {
        this.messageDispatchConstant = messageDispatchConstant;
    }

    @Bean
    DirectExchange messageDispatchExchange() {
        return new DirectExchange(messageDispatchConstant.getExchange(), true, false);
    }

    @Bean
    Queue messageDispatchQueue() {
        return QueueBuilder.durable(messageDispatchConstant.getQueue())
                .deadLetterExchange(messageDispatchConstant.getDeadExchange())
                .deadLetterRoutingKey(messageDispatchConstant.getDeadRoutingKey())
                .build();
    }

    @Bean
    Binding messageDispatchBinding() {
        return BindingBuilder.bind(messageDispatchQueue())
                .to(messageDispatchExchange())
                .with(messageDispatchConstant.getRoutingKey());
    }

    @Bean
    DirectExchange deadMessageDispatchExchange() {
        return new DirectExchange(messageDispatchConstant.getDeadExchange(), true, false);
    }

    @Bean
    Queue deadMessageDispatchQueue() {
        return QueueBuilder.durable(messageDispatchConstant.getDeadQueue()).build();
    }

    @Bean
    Binding deadMessageDispatchBinding() {
        return BindingBuilder.bind(deadMessageDispatchQueue())
                .to(deadMessageDispatchExchange())
                .with(messageDispatchConstant.getDeadRoutingKey());
    }
}
