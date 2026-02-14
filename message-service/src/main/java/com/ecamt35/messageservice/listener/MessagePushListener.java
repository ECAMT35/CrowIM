package com.ecamt35.messageservice.listener;

import com.ecamt35.messageservice.constant.RabbitMQConstant;
import com.ecamt35.messageservice.model.bo.SendMessageBo;
import com.ecamt35.messageservice.websocket.MessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessagePushListener {

    @Resource
    private MessageService messageService;

    @RabbitListener(queues = "#{rabbitMQConstant.getWebsocketMessageQueue()}")
    public void pushMessageOnline(SendMessageBo sendMessageBo) {
        messageService.sendMessageToUser(sendMessageBo);
    }

}
