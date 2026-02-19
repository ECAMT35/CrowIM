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

        String targetSid = notification.getSessionId();
        if (targetSid == null || targetSid.isBlank()) {
            log.warn("Skip offline notify because sessionId is blank: {}", notification);
            return;
        }

        Channel ch = userChannelRegistry.getRegisteredChannel(notification.getUserId(), notification.getDeviceId());
        if (ch == null) {
            log.info("No local channel found for userId={}, deviceId={}, ignore", notification.getUserId(), notification.getDeviceId());
            return;
        }

        ch.eventLoop().execute(() -> {
            if (!ch.isActive()) {
                return;
            }
            // 关闭指定 sessionId 的旧连接，但避免误关新连接
            String localSid = ch.attr(UserChannelRegistry.SESSION_ID_KEY).get();
            if (localSid == null) {
                log.info("Ignore offline because local sessionId is null (maybe not committed or already unregistered), notifySid={}", targetSid);
                return;
            }

            // 防误关
            if (!localSid.equals(targetSid)) {
                log.info("SessionId mismatch, ignore offline. userId={}, deviceId={}, local={}, notify={}",
                        notification.getUserId(), notification.getDeviceId(), localSid, targetSid);
                return;
            }

            Boolean reg = ch.attr(UserChannelRegistry.REGISTERED_KEY).get();
            if (reg == null || !reg) {
                log.info("Ignore offline because channel not registered yet, userId={}, deviceId={}, sid={}",
                        notification.getUserId(), notification.getDeviceId(), localSid);
                return;
            }

            log.info("Closing old channel for userId={}, deviceId={}, sessionId={}", notification.getUserId(), notification.getDeviceId(), localSid);

            userChannelRegistry.unregisterAsync(ch);
            ch.close();
        });

    }
}
