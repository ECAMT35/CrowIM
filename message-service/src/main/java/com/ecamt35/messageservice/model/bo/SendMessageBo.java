package com.ecamt35.messageservice.model.bo;

import lombok.Data;

@Data
public class SendMessageBo {
    private Long targetUserId;
    private String message;
    private Long senderId;
    private Long messageId;
    private Long timestamp;
}
