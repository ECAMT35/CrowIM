package com.ecamt35.messageservice.model.dto;

import lombok.Data;

@Data
public class ImMessageDto {
    private Integer packetType;
    private Long messageId;
    // SINGLE_CHAT(1)、GROUP_CHAT(2)
    private Integer chatType;
    // 消息类型：1-文本，2-图片
    private Integer messageType;
    private Long receiverId;
    private String content;
}

