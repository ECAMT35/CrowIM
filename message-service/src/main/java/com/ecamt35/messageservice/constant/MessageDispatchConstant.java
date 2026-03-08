package com.ecamt35.messageservice.constant;

import org.springframework.stereotype.Component;

/**
 * 发送消息后的异步分发队列常量。
 */
@Component
public class MessageDispatchConstant {

    public String getExchange() {
        return "message-dispatch.direct";
    }

    public String getQueue() {
        return "message-dispatch.queue";
    }

    public String getRoutingKey() {
        return "message-dispatch";
    }

    public String getDeadExchange() {
        return "dead.message-dispatch.direct";
    }

    public String getDeadQueue() {
        return "dead.message-dispatch.queue";
    }

    public String getDeadRoutingKey() {
        return "dead.message-dispatch";
    }
}
