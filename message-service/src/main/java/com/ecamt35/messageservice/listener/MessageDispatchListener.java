package com.ecamt35.messageservice.listener;

import com.ecamt35.messageservice.model.bo.MessageDispatchBo;
import com.ecamt35.messageservice.service.MessageDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消息分发任务消费者。
 */
@Component
@RequiredArgsConstructor
public class MessageDispatchListener {

    private final MessageDispatchService messageDispatchService;

    @RabbitListener(queues = "#{messageDispatchConstant.getQueue()}")
    public void handleDispatch(MessageDispatchBo dispatchBo) {
        messageDispatchService.handleDispatch(dispatchBo);
    }
}
