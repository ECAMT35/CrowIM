package com.ecamt35.messageservice.config;

import com.ecamt35.messageservice.constant.RelationPushConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelationPushMQConfig {

    private final RelationPushConstant relationPushConstant;

    public RelationPushMQConfig(RelationPushConstant relationPushConstant) {
        this.relationPushConstant = relationPushConstant;
    }

    @Bean
    DirectExchange websocketRelationExchange() {
        return new DirectExchange(relationPushConstant.getExchange(), true, false);
    }

    @Bean
    Queue websocketRelationQueue() {
        return QueueBuilder.durable(relationPushConstant.getQueue()).build();
    }

    @Bean
    Binding websocketRelationBinding() {
        return BindingBuilder.bind(websocketRelationQueue())
                .to(websocketRelationExchange())
                .with(relationPushConstant.getRoutingKey());
    }
}
