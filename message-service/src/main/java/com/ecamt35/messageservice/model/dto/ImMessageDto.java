package com.ecamt35.messageservice.model.dto;

import lombok.Data;

@Data
public class ImMessageDto {
    private Integer type; // 1=sent, 2=ack_delivered, 3=ack_read, 4=get_read,
    private Long messageId;
    // 消息类型：SINGLE_CHAT(0)、GROUP_CHAT(1)
    private Integer chatType;
    private Long receiverId;
    private String content;
}

