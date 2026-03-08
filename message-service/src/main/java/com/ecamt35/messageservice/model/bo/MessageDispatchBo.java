package com.ecamt35.messageservice.model.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息异步分发任务。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageDispatchBo {
    private Long messageId;
    private Long conversationId;
    private Integer chatType;
    private Integer msgType;
    private Long senderId;
    private String content;
    private Long sendTime;
    private Long groupId;
    private Long seq;
}
