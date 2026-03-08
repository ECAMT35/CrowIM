package com.ecamt35.messageservice.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RelationAckVo {
    private String requestId;
    private String op;
    private Integer code;
    private String message;
    private Object data;
}
