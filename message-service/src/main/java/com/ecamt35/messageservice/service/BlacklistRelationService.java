package com.ecamt35.messageservice.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Snowflake;
import com.ecamt35.messageservice.constant.RelationCacheKeyConstant;
import com.ecamt35.messageservice.mapper.BlacklistEdgeMapper;
import com.ecamt35.messageservice.mapper.FriendLinkMapper;
import com.ecamt35.messageservice.model.entity.BlacklistEdge;
import com.ecamt35.messageservice.util.RedisCacheClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BlacklistRelationService {

    private static final Duration BLACK_SET_TTL = Duration.ofDays(7);

    private final Snowflake snowflake;
    private final BlacklistEdgeMapper blacklistEdgeMapper;
    private final FriendLinkMapper friendLinkMapper;
    private final RedisCacheClient cacheClient;
    private final RelationEventPublisher relationEventPublisher;

    /**
     * 添加黑名单并解除好友关系。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> blacklistAdd(Long userId, Map<String, Object> payload) {
        Long targetId = requireLong(payload, "targetUserId");
        if (userId.equals(targetId)) {
            throw new IllegalArgumentException("cannot blacklist self");
        }

        upsertBlackEdge(userId, targetId);
        evictCacheKeyAfterCommit(blackSetKey(userId));

        friendLinkMapper.removeActive(userId, targetId);
        friendLinkMapper.removeActive(targetId, userId);
        evictCacheKeyAfterCommit(friendSetKey(userId));
        evictCacheKeyAfterCommit(friendSetKey(targetId));

        relationEventPublisher.emitEvent(Set.of(targetId), "BLACKLIST_ADDED", Map.of(
                "userId", userId,
                "targetUserId", targetId
        ));

        return Map.of("blocked", true);
    }

    /**
     * 取消黑名单。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> blacklistRemove(Long userId, Map<String, Object> payload) {
        Long targetId = requireLong(payload, "targetUserId");
        blacklistEdgeMapper.removeActive(userId, targetId);
        evictCacheKeyAfterCommit(blackSetKey(userId));
        return Map.of("removed", true);
    }

    /**
     * 查询黑名单列表。
     */
    public Map<String, Object> blacklistList(Long userId) {
        Set<Long> blockedIds = listBlackTargetIds(userId);
        return Map.of("blockedIds", blockedIds == null ? Collections.emptySet() : blockedIds);
    }

    /**
     * 是否被 owner 用户拉黑。
     */
    public boolean isBlacklisted(Long ownerId, Long targetId) {
        if (ownerId == null || targetId == null) {
            return false;
        }
        Set<Long> blocked = listBlackTargetIds(ownerId);
        return blocked.contains(targetId);
    }

    /**
     * 查询用户黑名单目标集合（缓存优先）。
     */
    public Set<Long> listBlackTargetIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return cacheClient.getOrLoadSet(
                blackSetKey(userId),
                BLACK_SET_TTL,
                Long.class,
                () -> blacklistEdgeMapper.listActiveTargetIds(userId)
        );
    }

    /**
     * 立即失效黑名单缓存。
     */
    public void evictBlackSet(Long userId) {
        if (userId == null) {
            return;
        }
        cacheClient.evict(blackSetKey(userId));
    }

    /**
     * 在事务提交后失效黑名单缓存，避免未提交事务触发脏回填。
     */
    public void evictBlackSetAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        evictCacheKeyAfterCommit(blackSetKey(userId));
    }

    private void upsertBlackEdge(Long userId, Long targetId) {
        BlacklistEdge any = blacklistEdgeMapper.findAny(userId, targetId);
        if (any == null) {
            BlacklistEdge edge = new BlacklistEdge();
            edge.setId(snowflake.nextId());
            edge.setUserId(userId);
            edge.setTargetId(targetId);
            edge.setDeleted(0);
            blacklistEdgeMapper.insert(edge);
            return;
        }
        if (any.getDeleted() != null && any.getDeleted() == 1) {
            // blacklist_edge 开启逻辑删除，恢复已删除记录需要显式 SQL
            blacklistEdgeMapper.restoreDeletedById(any.getId());
        }
    }

    private Long requireLong(Map<String, Object> payload, String key) {
        Long value = Convert.toLong(payload.get(key));
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private void evictCacheKeyAfterCommit(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            String cacheKey = key;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheClient.evict(cacheKey);
                }
            });
            return;
        }
        cacheClient.evict(key);
    }

    private String blackSetKey(Long userId) {
        return RelationCacheKeyConstant.BLACK_SET_PREFIX + userId;
    }

    private String friendSetKey(Long userId) {
        return RelationCacheKeyConstant.FRIEND_SET_PREFIX + userId;
    }
}
