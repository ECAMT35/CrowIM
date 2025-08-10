package com.ecamt35.messageservice.constant;

import com.ecamt35.messageservice.config.NodeName;

public class RabbitMQConstant {
    public static final String WEBSOCKET_MESSAGE_EXCHANGE = "websocket-message-" + NodeName.NODE_NAME + ".direct";
    public static final String WEBSOCKET_MESSAGE_QUEUE = "websocket-message-" + NodeName.NODE_NAME + ".queue";
    public static final String WEBSOCKET_MESSAGE_KEY = "websocket-message-" + NodeName.NODE_NAME;

    public static final String DEAD_WEBSOCKET_MESSAGE_EXCHANGE = "dead.websocket-message-" + NodeName.NODE_NAME + ".direct";
    public static final String DEAD_WEBSOCKET_MESSAGE_QUEUE = "dead.websocket-message-" + NodeName.NODE_NAME + ".queue";
    public static final String DEAD_WEBSOCKET_MESSAGE_ROUTING_KEY = "dead.websocket-message-" + NodeName.NODE_NAME;
}