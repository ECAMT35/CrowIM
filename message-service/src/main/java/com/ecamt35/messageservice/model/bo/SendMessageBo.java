package com.ecamt35.messageservice.model.bo;

import lombok.Data;

@Data
public class SendMessageBo {
    private Long receiverId;
    private String message;
    private Integer messageType;
    private Long senderId;
    private Long messageId;
    private Long sendTime;
}
