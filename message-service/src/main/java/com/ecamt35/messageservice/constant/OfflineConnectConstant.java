package com.ecamt35.messageservice.constant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OfflineConnectConstant {

    @Value("${node-name}")
    private String nodeName;

    public String getOfflineConnectExchange() {
        // 下线通知使用统一的 direct 交换机，所有节点共享
        return "offline-connect.direct";
    }

    public String getOfflineConnectQueue() {
        return "offline-connect-" + nodeName + ".queue";
    }

    public String getOfflineConnectRoutingKey() {
        return "offline-connect-" + nodeName;
    }

}