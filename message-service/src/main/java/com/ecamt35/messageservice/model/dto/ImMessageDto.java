package com.ecamt35.messageservice.model.dto;

import lombok.Data;

@Data
public class ImMessageDto {
    private Long messageId;
    // 消息类型：SINGLE_CHAT(0)、GROUP_CHAT(1)
    private Integer message_type;
    private Long receiverId;
    private String content;
}

