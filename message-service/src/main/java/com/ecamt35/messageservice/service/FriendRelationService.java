package com.ecamt35.messageservice.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Snowflake;
import com.ecamt35.messageservice.constant.RelationCacheKeyConstant;
import com.ecamt35.messageservice.constant.RelationStatusConstant;
import com.ecamt35.messageservice.mapper.FriendApplyMapper;
import com.ecamt35.messageservice.mapper.FriendLinkMapper;
import com.ecamt35.messageservice.model.entity.FriendApply;
import com.ecamt35.messageservice.model.entity.FriendLink;
import com.ecamt35.messageservice.util.RedisCacheClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FriendRelationService {

    private static final Duration FRIEND_SET_TTL = Duration.ofDays(7);

    private final Snowflake snowflake;
    private final FriendApplyMapper friendApplyMapper;
    private final FriendLinkMapper friendLinkMapper;
    private final BlacklistRelationService blacklistRelationService;
    private final RedisCacheClient cacheClient;
    private final RelationEventPublisher relationEventPublisher;

    /**
     * 发起好友申请。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> friendApply(Long userId, Map<String, Object> payload) {
        Long targetId = requireLong(payload, "targetUserId");
        String applyMessage = trimToNull(Convert.toStr(payload.get("applyMessage")));
        if (userId.equals(targetId)) {
            throw new IllegalArgumentException("cannot apply self");
        }
        if (blacklistRelationService.isBlacklisted(targetId, userId)) {
            throw new IllegalStateException("blocked by target user");
        }
        if (isMutualFriend(userId, targetId)) {
            return Map.of("status", "already_friends");
        }

        FriendApply latest = friendApplyMapper.findLatestActive(userId, targetId);
        if (latest != null && Objects.equals(latest.getStatus(), RelationStatusConstant.APPLY_PENDING)) {
            return Map.of(
                    "status", "pending",
                    "applyId", latest.getId()
            );
        }

        FriendApply apply = new FriendApply();
        apply.setId(snowflake.nextId());
        apply.setApplicantId(userId);
        apply.setTargetId(targetId);
        apply.setStatus(RelationStatusConstant.APPLY_PENDING);
        apply.setApplyMessage(applyMessage);
        apply.setDeleted(0);
        friendApplyMapper.insert(apply);

        relationEventPublisher.emitEvent(Set.of(targetId), "FRIEND_APPLY_CREATED", Map.of(
                "applyId", apply.getId(),
                "applicantId", userId,
                "targetId", targetId
        ));

        return Map.of(
                "status", "pending",
                "applyId", apply.getId()
        );
    }

    /**
     * 审批好友申请。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> friendApplyDecide(Long userId, Map<String, Object> payload) {
        Long applyId = requireLong(payload, "applyId");
        boolean approve = Convert.toBool(payload.get("approve"), false);

        FriendApply apply = friendApplyMapper.selectById(applyId);
        if (apply == null || apply.getDeleted() != null && apply.getDeleted() == 1) {
            throw new IllegalArgumentException("apply not found");
        }
        if (!Objects.equals(apply.getTargetId(), userId)) {
            throw new IllegalStateException("no permission to decide this apply");
        }
        if (!Objects.equals(apply.getStatus(), RelationStatusConstant.APPLY_PENDING)) {
            return Map.of("status", "already_decided", "applyId", applyId);
        }

        int decisionStatus = approve ? RelationStatusConstant.APPLY_ACCEPTED : RelationStatusConstant.APPLY_REJECTED;
        Date decisionTime = new Date();
        int updated = friendApplyMapper.updateDecisionIfStatus(
                applyId,
                decisionStatus,
                userId,
                decisionTime,
                RelationStatusConstant.APPLY_PENDING
        );
        if (updated == 0) {
            return Map.of("status", "already_decided", "applyId", applyId);
        }

        apply.setStatus(decisionStatus);
        apply.setDecisionUserId(userId);
        apply.setDecisionTime(decisionTime);

        if (approve) {
            upsertFriendEdge(apply.getApplicantId(), apply.getTargetId());
            upsertFriendEdge(apply.getTargetId(), apply.getApplicantId());
            evictFriendSetAfterCommit(apply.getApplicantId());
            evictFriendSetAfterCommit(apply.getTargetId());
        }

        relationEventPublisher.emitEvent(Set.of(apply.getApplicantId(), apply.getTargetId()), "FRIEND_APPLY_DECIDED", Map.of(
                "applyId", apply.getId(),
                "applicantId", apply.getApplicantId(),
                "targetId", apply.getTargetId(),
                "approve", approve
        ));

        return Map.of("status", approve ? "accepted" : "rejected", "applyId", apply.getId());
    }

    /**
     * 获取好友列表。
     */
    public Map<String, Object> friendList(Long userId) {
        Set<Long> friendIds = listFriendTargetIds(userId);
        return Map.of("friendIds", friendIds == null ? Collections.emptySet() : friendIds);
    }

    /**
     * 获取当前用户收到的好友申请列表。
     */
    public Map<String, Object> friendApplyList(Long userId, Map<String, Object> payload) {
        Integer statusValue = Convert.toInt(payload.get("status"), RelationStatusConstant.APPLY_PENDING);
        Integer status = normalizeApplyStatus(statusValue);

        int pageNo = normalizePageNo(Convert.toInt(payload.get("pageNo"), 1));
        int pageSize = normalizePageSize(Convert.toInt(payload.get("pageSize"), 20));
        int offset = (pageNo - 1) * pageSize;

        List<FriendApply> rows = friendApplyMapper.listByTarget(userId, status, pageSize + 1, offset);
        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = rows.subList(0, pageSize);
        }

        return Map.of(
                "items", rows,
                "pageNo", pageNo,
                "pageSize", pageSize,
                "hasMore", hasMore
        );
    }

    /**
     * 删除双向好友关系。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> friendDelete(Long userId, Map<String, Object> payload) {
        Long targetId = requireLong(payload, "targetUserId");
        if (userId.equals(targetId)) {
            throw new IllegalArgumentException("cannot delete self");
        }
        friendLinkMapper.removeActive(userId, targetId);
        friendLinkMapper.removeActive(targetId, userId);
        evictFriendSetAfterCommit(userId);
        evictFriendSetAfterCommit(targetId);

        relationEventPublisher.emitEvent(Set.of(targetId), "FRIEND_DELETED", Map.of(
                "userId", userId,
                "targetUserId", targetId
        ));

        return Map.of("removed", true);
    }

    /**
     * 是否双向好友。
     */
    public boolean isMutualFriend(Long userA, Long userB) {
        return hasFriendEdge(userA, userB) && hasFriendEdge(userB, userA);
    }

    /**
     * 是否存在单向好友边。
     */
    public boolean hasFriendEdge(Long userId, Long targetId) {
        if (userId == null || targetId == null) {
            return false;
        }
        Set<Long> friendSet = listFriendTargetIds(userId);
        return friendSet.contains(targetId);
    }

    /**
     * 查询用户好友目标集合（缓存优先）。
     */
    public Set<Long> listFriendTargetIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return cacheClient.getOrLoadSet(
                friendSetKey(userId),
                FRIEND_SET_TTL,
                Long.class,
                () -> friendLinkMapper.listActiveTargetIds(userId)
        );
    }

    /**
     * 立即失效好友集合缓存。
     */
    public void evictFriendSet(Long userId) {
        if (userId == null) {
            return;
        }
        cacheClient.evict(friendSetKey(userId));
    }

    /**
     * 在事务提交后失效好友缓存，避免未提交事务触发脏回填。
     */
    public void evictFriendSetAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            Long uid = userId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheClient.evict(friendSetKey(uid));
                }
            });
            return;
        }
        cacheClient.evict(friendSetKey(userId));
    }

    private void upsertFriendEdge(Long userId, Long targetId) {
        FriendLink any = friendLinkMapper.findAny(userId, targetId);
        if (any == null) {
            FriendLink link = new FriendLink();
            link.setId(snowflake.nextId());
            link.setUserId(userId);
            link.setTargetId(targetId);
            link.setDeleted(0);
            friendLinkMapper.insert(link);
            return;
        }
        if (any.getDeleted() != null && any.getDeleted() == 1) {
            friendLinkMapper.restoreDeletedById(any.getId());
        }
    }

    private Long requireLong(Map<String, Object> payload, String key) {
        Long value = Convert.toLong(payload.get(key));
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer normalizeApplyStatus(Integer status) {
        if (status == null || status == -1) {
            return null;
        }
        if (status != RelationStatusConstant.APPLY_PENDING
                && status != RelationStatusConstant.APPLY_ACCEPTED
                && status != RelationStatusConstant.APPLY_REJECTED
                && status != RelationStatusConstant.APPLY_CANCELED) {
            throw new IllegalArgumentException("invalid status");
        }
        return status;
    }

    private int normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1;
        }
        return pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private String friendSetKey(Long userId) {
        return RelationCacheKeyConstant.FRIEND_SET_PREFIX + userId;
    }
}
