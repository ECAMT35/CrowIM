package com.ecamt35.messageservice.model.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CommonPacketDto {
    private Integer packetType;
    private Map<String, Object> data;
}
