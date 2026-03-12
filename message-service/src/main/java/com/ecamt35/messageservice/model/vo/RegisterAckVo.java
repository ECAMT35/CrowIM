package com.ecamt35.messageservice.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegisterAckVo {
    private String requestId;
    private Long userId;
    private String deviceId;
    private String nodeName;
    private Long serverTimestamp;
    private Integer code;
    private String message;
}
