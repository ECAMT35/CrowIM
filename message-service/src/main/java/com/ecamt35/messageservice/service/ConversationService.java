package com.ecamt35.messageservice.service;

import cn.hutool.core.lang.Snowflake;
import com.ecamt35.messageservice.mapper.ConversationMapper;
import com.ecamt35.messageservice.mapper.ConversationMemberMapper;
import com.ecamt35.messageservice.model.entity.Conversation;
import com.ecamt35.messageservice.model.entity.ConversationMember;
import com.ecamt35.messageservice.util.RedisCacheClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationService {

    private static final Duration PRIVATE_CONV_TTL = Duration.ofHours(6);
    private static final Duration USER_CONV_IDS_TTL = Duration.ofMinutes(3);

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper memberMapper;
    private final RedisCacheClient cacheClient;
    private final Snowflake snowflake;

    public Long getOrCreatePrivateConversationIdOrThrow(Long senderId, Long receiverId) {

        // 权限校验
        // 是否被对方拉黑
        if (isBlacklisted(receiverId, senderId)) {
            throw new IllegalStateException("blocked by receiver");
        }
        // 是否允许陌生人发起对话
        boolean allowStranger = isAllowStrangerConversation(receiverId);
        // 不允许陌生人
        if (!allowStranger) {
            if (!isFriend(senderId, receiverId)) {
                throw new IllegalStateException("not friends and stranger chat disabled");
            }
        }

        // 允许陌生人/是好友->conversation_member检查
        long a = Math.min(senderId, receiverId);
        long b = Math.max(senderId, receiverId);

        // 查旧会话
        Conversation existed = findPrivateCached(a, b);
        if (existed != null) {
            log.info("existed conversation: {}", existed.getId());
            // 确保双方是 active member，避免历史退会/删除导致异常
            boolean changed1 = ensureMemberActive(existed.getId(), senderId);
            boolean changed2 = ensureMemberActive(existed.getId(), receiverId);
            if (changed1) evictUserConversationIds(senderId);
            if (changed2) evictUserConversationIds(receiverId);
            return existed.getId();
        }

        // 创建会话（幂等）
        long convId = snowflake.nextId();
        Conversation c = new Conversation();
        c.setId(convId);
        c.setType(0);
        c.setPeerA(a);
        c.setPeerB(b);
        c.setDeleted(0);

        try {
            conversationMapper.insert(c);
        } catch (DuplicateKeyException e) {
            // 并发创建
            Conversation again = conversationMapper.findPrivate(a, b);
            if (again != null) {
                evictPrivateConversation(a, b);
                ensureMemberActive(again.getId(), senderId);
                ensureMemberActive(again.getId(), receiverId);
                evictUserConversationIds(senderId);
                evictUserConversationIds(receiverId);
                return again.getId();
            }
            throw e;
        }
        // 创建/恢复会话成员关系
        ensureMemberActive(c.getId(), senderId);
        ensureMemberActive(c.getId(), receiverId);

        evictPrivateConversation(a, b);
        evictUserConversationIds(senderId);
        evictUserConversationIds(receiverId);

        return c.getId();
    }

    /**
     * 确保 member 存在且 deleted=0；若 deleted=1 则恢复会话成员关系
     */
    private boolean ensureMemberActive(Long convId, Long userId) {
        ConversationMember any = memberMapper.findAny(convId, userId);
        if (any != null && any.getDeleted() != null && any.getDeleted() == 0) {
            return false;
        }

        if (any != null && any.getDeleted() != null && any.getDeleted() == 1) {
            any.setDeleted(0);
            memberMapper.updateById(any);
            return true;
        }

        ConversationMember m = new ConversationMember();
        long conversationMemberId = snowflake.nextId();
        m.setId(conversationMemberId);
        m.setConversationId(convId);
        m.setUserId(userId);
        m.setRole(1);
        m.setMute(0);
        m.setLastReadSeq(0L);
        m.setDeleted(0);

        try {
            memberMapper.insert(m);
            return true;
        } catch (DuplicateKeyException ignore) {
            return true;
        }
    }

    /**
     * 查询用户参与的会话ID列表（SUMMARY 用）
     */
    public List<Long> listConversationIdsForUser(Long userId) {
        return listConversationIdsForUserCached(userId);
    }

    /**
     * 是否在receiver黑名单
     */
    private boolean isBlacklisted(Long receiverId, Long senderId) {
        // todo: Redis set / DB
        return false;
    }

    /**
     * 是否好友
     */
    private boolean isFriend(Long senderId, Long receiverId) {
        // todo: Redis set / DB
        return false;
    }

    /**
     * receiver 是否允许作为陌生人发起对话
     */
    private boolean isAllowStrangerConversation(Long receiverId) {
        // todo: privacy setting
//        return false;
        return true;
    }

    /**
     * 查询私聊会话（缓存优先）
     */
    public Conversation findPrivateCached(long peerA, long peerB) {
        long a = Math.min(peerA, peerB);
        long b = Math.max(peerA, peerB);

        // key: im:conv:private:{a}:{b}
        return cacheClient.getOrLoadById(
                "im:conv:private:",
                a + ":" + b,
                PRIVATE_CONV_TTL,
                Conversation.class,
                ignore -> conversationMapper.findPrivate(a, b)
        );
    }

    /**
     * 查询用户会话ID列表（缓存优先）
     */
    public List<Long> listConversationIdsForUserCached(long userId) {
        // key: im:conv:ids:user:{userId}
        return cacheClient.getOrLoadById(
                "im:conv:ids:user:",
                userId,
                USER_CONV_IDS_TTL,
                List.class,
                conversationMapper::listConversationIdsForUser
        );
    }

    /**
     * 失效用户会话列表缓存（加入/退出/创建私聊/删除会话等）
     */
    public void evictUserConversationIds(long userId) {
        cacheClient.evict("im:conv:ids:user:" + userId);
    }

    /**
     * 失效私聊会话缓存（创建/删除/恢复等）
     */
    public void evictPrivateConversation(long peerA, long peerB) {
        long a = Math.min(peerA, peerB);
        long b = Math.max(peerA, peerB);
        cacheClient.evict("im:conv:private:" + a + ":" + b);
    }
}