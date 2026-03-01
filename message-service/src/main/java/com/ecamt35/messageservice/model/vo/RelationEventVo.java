package com.ecamt35.messageservice.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RelationEventVo {
    private String eventType;
    private Long eventId;
    private Object data;
}
