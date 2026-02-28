package com.ecamt35.messageservice.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class PullMessagesRespVo {

    private Long conversationId;
    // 本轮拉取冻结上界（包含）
    private Long upperBoundSeq;
    // 本次返回最后一条消息的 seq；若本页为空则等于请求 afterSeq
    private Long nextAfterSeq;
    // nextAfterSeq < upperBoundSeq
    private Boolean hasMore;
    private List<MessageBriefVo> messages;
    // 同一个报文是否应该重试
    private Integer retryAfterMs;

    @Data
    public static class MessageBriefVo {
        // messageId
        private Long id;
        private Long clientMsgId;
        private Long conversationId;
        private Long seq;
        private Long senderId;
        private Integer msgType;
        private String content;
        private Long sendTime;
    }
}