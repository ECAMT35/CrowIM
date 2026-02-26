package com.ecamt35.messageservice.model.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SendMessageBo {
    private Long targetUserId;
    private String message;
    private Integer chatType;
    private Integer messageType;
    private Long senderId;
    private Long messageId;
    private Long sendTime;
    private String receiverDeviceId;
    private Long conversationId;
    private Long seq;
}
