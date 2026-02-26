package com.ecamt35.messageservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SummaryService {

    private final ConversationService conversationService;
    private final CursorService cursorService;

    /***
     * 构建用户的所有会话摘要（SUMMARY）
     * - lastSeq：会话最新 seq（Redis 优先，DB max(seq) 兜底，可回填）
     * - readSeq：用户已读游标（Redis 优先，DB last_read_seq 兜底，可回填）
     * - unread：max(0, lastSeq - readSeq)
     *
     * @return convId -> {lastSeq, readSeq, unread} 的映射
     */
    public Map<Long, Map<String, Long>> buildSummary(Long userId) {
        // 获取用户参与的会话 ID 列表
        List<Long> convIds = conversationService.listConversationIdsForUser(userId);
        if (convIds == null || convIds.isEmpty()) return Collections.emptyMap();

        Map<Long, Map<String, Long>> res = new HashMap<>();
        for (Long convId : convIds) {
            long last = cursorService.getLastSeq(convId);
            long read = cursorService.getRead(userId, convId);

            Map<String, Long> one = new HashMap<>();
            one.put("lastSeq", last);
            one.put("readSeq", read);
            one.put("unread", Math.max(0L, last - read));
            res.put(convId, one);
        }
        return res;
    }
}