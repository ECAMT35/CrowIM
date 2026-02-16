package com.ecamt35.messageservice.listener;

import com.ecamt35.messageservice.model.bo.OfflineNotificationBo;
import com.ecamt35.messageservice.websocket.UserChannelRegistry;
import io.netty.channel.Channel;
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
        // 关闭指定 sessionId 的旧连接，但避免误关新连接
        Channel ch = userChannelRegistry.getRegisteredChannel(notification.getUserId(), notification.getDeviceId());
        if (ch == null) {
            log.info("No local channel found for userId={}, deviceId={}, ignore", notification.getUserId(), notification.getDeviceId());
            return;
        }
        // 防误关
        String localSessionId = ch.attr(UserChannelRegistry.SESSION_ID_KEY).get();
        if (notification.getSessionId() != null && !notification.getSessionId().isBlank()
                && localSessionId != null && !localSessionId.equals(notification.getSessionId())) {
            log.info("SessionId mismatch, ignore offline. userId={}, deviceId={}, local={}, notify={}",
                    notification.getUserId(), notification.getDeviceId(), localSessionId, notification.getSessionId());
            return;
        }

        log.info("Closing old channel for userId={}, deviceId={}, sessionId={}",
                notification.getUserId(), notification.getDeviceId(), localSessionId);

        userChannelRegistry.unregisterUser(ch);
        if (ch.isActive()) {
            ch.close();
        }
    }
}
