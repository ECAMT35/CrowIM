package com.ecamt35.messageservice.listener;

import com.ecamt35.messageservice.model.bo.SendMessageBo;
import com.ecamt35.messageservice.service.DeliveryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessagePushListener {

    @Resource
    private DeliveryService deliveryService;

    @RabbitListener(queues = "#{messagePushConstant.getWebsocketMessageQueue()}")
    public void pushMessageOnline(SendMessageBo sendMessageBo) {
        deliveryService.deliverToUserDevices(sendMessageBo);
    }

}
