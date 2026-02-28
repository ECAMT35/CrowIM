package com.ecamt35.messageservice.service;

import com.ecamt35.messageservice.mapper.ConversationMemberMapper;
import com.ecamt35.messageservice.mapper.MessageMapper;
import com.ecamt35.messageservice.model.entity.Conversation;
import com.ecamt35.messageservice.model.entity.ConversationMember;
import com.ecamt35.messageservice.model.entity.Message;
import com.ecamt35.messageservice.model.vo.PullMessagesRespVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ConversationMemberMapper memberMapper;
    private final MessageMapper messageMapper;
    private final ConversationService conversationService;
    private final CursorService cursorService;

    /**
     * 拉取消息（按 seq 游标），保证不因雪花 ID 乱序而漏。
     *
     * @param userId        当前用户
     * @param convId        会话ID
     * @param afterSeq      已拥有的最大 seq（不含）
     * @param limit         分页大小
     * @param upperBoundSeq 冻结上界（含）；为空则使用 DB max(seq) 作为上界
     */
    public PullMessagesRespVo pullMessages(Long userId,
                                           Long convId,
                                           Long afterSeq,
                                           Integer limit,
                                           Long upperBoundSeq) {

        if (userId == null || convId == null) {
            throw new IllegalArgumentException("userId/convId is null");
        }
        if (afterSeq == null || afterSeq < 0) afterSeq = 0L;

        int pageSize = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        // 校验会话存在
        Conversation conv = conversationService.selectById(convId);
        if (conv == null) {
            return null;
        }

        // 权限校验
        if (!hasPermission(userId, conv)) {
            return null;
        }

        // upperBoundSeq 为空时，优先用 Redis lastSeq
        // 因为 Redis lastSeq 可能自增后消息还没落盘导致虚高/gap, 注意使用DB进行兜底
        Long ub = upperBoundSeq;
        boolean ubFromRedis = false;
        if (ub == null || ub < 0) {
            long lastSeq = cursorService.getLastSeq(convId);
            log.info("Try to get lastSeq from Redis as upperBoundSeq, convId: {}", convId);
            ub = Math.max(0L, lastSeq);
            ubFromRedis = true;
        }

        // afterSeq 已经追平/超过上界，直接返回空页，本轮结束，无需退避
        if (afterSeq >= ub) {
            PullMessagesRespVo resp = emptyResp(convId, ub, afterSeq);
            resp.setRetryAfterMs(-1);
            return resp;
        }

        // 按 seq 拉取区间消息
        List<Message> list = messageMapper.listBySeqRange(convId, afterSeq, ub, pageSize);

        // 尾部纠偏：如果 afterSeq < ub 但这页为空，很可能是 Redis lastSeq 虚高/消息未落库导致。
        // 这时用 DB max(seq) 把 ub 回落到已落库上界，并把纠偏后的 ub 返回给客户端，避免客户端卡死重试。
        if (list == null || list.isEmpty()) {
            log.info("No messages found for convId {}", convId);
            // 只有 ub 来自 Redis 时才做 DB max 纠偏，避免空页也打 DB
            if (!ubFromRedis) {
                PullMessagesRespVo resp = emptyResp(convId, ub, afterSeq);
                resp.setRetryAfterMs(-1);
                return resp;
            }

            Long dbMaxObj = messageMapper.findMaxSeqByConvId(convId);
            long dbMax = (dbMaxObj == null) ? 0L : dbMaxObj;

            long effectiveUb = Math.min(ub, dbMax);
            if (effectiveUb != ub) {
                log.info("Override upperBoundSeq use lastSeq from DB, convId: {}", convId);
                ub = effectiveUb;

                if (afterSeq >= ub) {
                    PullMessagesRespVo resp = emptyResp(convId, ub, afterSeq);
                    resp.setRetryAfterMs(200);
                    return resp;
                }

                // 纠偏后再查一次，可能刚好有落库可取
                list = messageMapper.listBySeqRange(convId, afterSeq, ub, pageSize);
            }

            // 纠偏后仍为空
            if (list == null || list.isEmpty()) {
                log.info("Try again but no messages found for convId {}", convId);
                PullMessagesRespVo resp = emptyResp(convId, ub, afterSeq);
                resp.setRetryAfterMs(400);
                return resp;
            }
        }

        // 正常返回消息页
        long next = afterSeq;
        List<PullMessagesRespVo.MessageBriefVo> vos = new ArrayList<>(list.size());
        for (Message m : list) {
            PullMessagesRespVo.MessageBriefVo vo = new PullMessagesRespVo.MessageBriefVo();
            vo.setId(m.getId());
            vo.setClientMsgId(m.getClientMsgId());
            vo.setConversationId(m.getConversationId());
            vo.setSeq(m.getSeq());
            vo.setSenderId(m.getSenderId());
            vo.setMsgType(m.getMsgType());
            vo.setContent(m.getContent());
            vo.setSendTime(m.getSendTime());
            vos.add(vo);
            if (m.getSeq() != null) next = Math.max(next, m.getSeq());
        }

        PullMessagesRespVo resp = new PullMessagesRespVo();
        resp.setConversationId(convId);
        resp.setUpperBoundSeq(ub);
        resp.setNextAfterSeq(next);
        resp.setHasMore(next < ub);
        resp.setMessages(vos);
        resp.setRetryAfterMs(-1);
        return resp;
    }

    private boolean hasPermission(Long userId, Conversation conv) {
        Integer type = conv.getType();
        if (type == null) return false;

        if (type == 0) {
            // private, 看 conversation 表存的 peer_a/peer_b
            Long a = conv.getPeerA();
            Long b = conv.getPeerB();
            return (a != null && a.equals(userId)) || (b != null && b.equals(userId));
        }

        if (type == 1) {
            // group, 必须是 active member
            ConversationMember m = memberMapper.findActive(conv.getId(), userId);
            return m != null;
        }
        log.info("User {} has no permission to conversation {}", userId, conv.getId());
        return false;
    }

    private PullMessagesRespVo emptyResp(Long convId, Long ub, Long afterSeq) {
        PullMessagesRespVo resp = new PullMessagesRespVo();
        resp.setConversationId(convId);
        resp.setUpperBoundSeq(ub);
        resp.setNextAfterSeq(afterSeq);
        resp.setHasMore(false);
        resp.setMessages(List.of());
        return resp;
    }
}