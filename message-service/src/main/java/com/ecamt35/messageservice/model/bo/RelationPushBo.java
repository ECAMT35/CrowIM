package com.ecamt35.messageservice.model.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RelationPushBo {
    private Long targetUserId;
    private Integer packetType;
    private Object data;
    private String receiverDeviceId;
}
