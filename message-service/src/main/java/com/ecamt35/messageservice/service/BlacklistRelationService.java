package com.ecamt35.messageservice.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Snowflake;
import com.ecamt35.messageservice.mapper.BlacklistEdgeMapper;
import com.ecamt35.messageservice.mapper.FriendLinkMapper;
import com.ecamt35.messageservice.model.entity.BlacklistEdge;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BlacklistRelationService {

    private final Snowflake snowflake;
    private final BlacklistEdgeMapper blacklistEdgeMapper;
    private final FriendLinkMapper friendLinkMapper;
    private final RelationPermissionService relationPermissionService;
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
        relationPermissionService.evictBlackSet(userId);

        friendLinkMapper.removeActive(userId, targetId);
        friendLinkMapper.removeActive(targetId, userId);
        relationPermissionService.evictFriendSet(userId);
        relationPermissionService.evictFriendSet(targetId);

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
        relationPermissionService.evictBlackSet(userId);
        return Map.of("removed", true);
    }

    /**
     * 查询黑名单列表。
     */
    public Map<String, Object> blacklistList(Long userId) {
        Set<Long> blockedIds = blacklistEdgeMapper.listActiveTargetIds(userId);
        return Map.of("blockedIds", blockedIds == null ? Collections.emptySet() : blockedIds);
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
}
