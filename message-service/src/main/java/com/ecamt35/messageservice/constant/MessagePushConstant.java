package com.ecamt35.messageservice.constant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MessagePushConstant {

    @Value("${node-name}")
    private String nodeName;

    public String getWebsocketMessageExchange() {
        return "websocket-message-" + nodeName + ".direct";
    }

    public String getWebsocketMessageQueue() {
        return "websocket-message-" + nodeName + ".queue";
    }

    public String getWebsocketMessageKey() {
        return "websocket-message-" + nodeName;
    }

    public String getDeadWebsocketMessageExchange() {
        return "dead.websocket-message-" + nodeName + ".direct";
    }

    public String getDeadWebsocketMessageQueue() {
        return "dead.websocket-message-" + nodeName + ".queue";
    }

    public String getDeadWebsocketMessageRoutingKey() {
        return "dead.websocket-message-" + nodeName;
    }
}