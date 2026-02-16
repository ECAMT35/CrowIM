package com.ecamt35.messageservice.listener;

import com.ecamt35.messageservice.model.bo.OfflineNotificationBo;
import com.ecamt35.messageservice.websocket.UserChannelRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OfflineConnectListener {

    @Resource
    private UserChannelRegistry userChannelRegistry;

    @RabbitListener(queues = "#{offlineConnectConstant.getOfflineConnectQueue()}")
    public void handleOffline(OfflineNotificationBo notification) {
        log.info("Received offline notification: {}", notification);

        // todo
        System.out.println("Received offline notification: " + notification);
        // 根据 deviceId 查找本地连接并关闭
//        Channel channel = userChannelRegistry.getChannelByDeviceId(notification.getDeviceId());
//        if (channel != null && channel.isActive()) {
//            channel.close();
//            log.info("Closed local connection for device {} due to offline notification", notification.getDeviceId());
//        } else {
//            log.warn("Device {} not found locally or already closed", notification.getDeviceId());
//        }
    }
}