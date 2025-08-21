package com.ecamt35.messageservice.model.vo;

import lombok.Data;

@Data
public class MessageAckVo {
    private Long messageId;
    private Long sendTime;
}
