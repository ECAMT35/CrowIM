package com.ecamt35.messageservice.model.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfflineNotificationBo {
    private Long userId;          // 用户ID
    private String deviceId;       // 设备ID
    private String targetNode;     // 需下线的节点名称
    private String reason;         // 下线原因
    private String sessionId;      // 旧连接会话标识
}