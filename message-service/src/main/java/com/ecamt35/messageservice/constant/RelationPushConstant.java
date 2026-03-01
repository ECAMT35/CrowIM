package com.ecamt35.messageservice.constant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RelationPushConstant {

    @Value("${node-name}")
    private String nodeName;

    public String getExchange() {
        return "websocket-relation-" + nodeName + ".direct";
    }

    public String getQueue() {
        return "websocket-relation-" + nodeName + ".queue";
    }

    public String getRoutingKey() {
        return "websocket-relation-" + nodeName;
    }
}
