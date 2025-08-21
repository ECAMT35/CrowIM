package com.ecamt35.messageservice.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PushVo {
    private Integer type; //1=message, 2=ack
    private Object data;
}
