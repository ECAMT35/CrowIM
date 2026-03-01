package com.ecamt35.messageservice.listener;

import com.ecamt35.messageservice.model.bo.RelationPushBo;
import com.ecamt35.messageservice.service.RelationDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RelationPushListener {

    private final RelationDeliveryService relationDeliveryService;

    @RabbitListener(queues = "#{relationPushConstant.getQueue()}")
    public void pushRelationOnline(RelationPushBo pushBo) {
        relationDeliveryService.deliverToUserDevices(pushBo);
    }
}
