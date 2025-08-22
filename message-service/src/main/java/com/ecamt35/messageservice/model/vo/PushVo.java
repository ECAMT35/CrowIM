package com.ecamt35.messageservice.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PushVo {
    private Integer packetType; // MessageType
    private Object data;
}
