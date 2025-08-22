package com.ecamt35.messageservice.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReadMessageResult {
    private Long receiverId;
    private List<Long> readMessageIdList;
}
