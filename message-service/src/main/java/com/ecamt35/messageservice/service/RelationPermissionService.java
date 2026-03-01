package com.ecamt35.messageservice.service;

import com.ecamt35.messageservice.mapper.BlacklistEdgeMapper;
import com.ecamt35.messageservice.mapper.FriendLinkMapper;
import com.ecamt35.messageservice.mapper.UserPrivacySettingMapper;
import com.ecamt35.messageservice.model.entity.UserPrivacySetting;
import com.ecamt35.messageservice.util.RedisCacheClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RelationPermissionService {

    private static final Duration FRIEND_SET_TTL = Duration.ofDays(7);
    private static final Duration BLACK_SET_TTL = Duration.ofDays(7);
    private static final Duration PRIVACY_TTL = Duration.ofDays(7);

    private final FriendLinkMapper friendLinkMapper;
    private final BlacklistEdgeMapper blacklistEdgeMapper;
    private final UserPrivacySettingMapper userPrivacySettingMapper;
    private final RedisCacheClient cacheClient;

    /**
     * 是否被对方拉黑
     */
    public boolean isBlacklisted(Long ownerId, Long targetId) {
        if (ownerId == null || targetId == null) return false;
        Set<Long> blocked = cacheClient.getOrLoadSet(
                blackSetKey(ownerId),
                BLACK_SET_TTL,
                Long.class,
                () -> blacklistEdgeMapper.listActiveTargetIds(ownerId)
        );
        return blocked.contains(targetId);
    }

    /**
     * 是否双向好友
     */
    public boolean isMutualFriend(Long userA, Long userB) {
        return hasFriendEdge(userA, userB) && hasFriendEdge(userB, userA);
    }

    public boolean hasFriendEdge(Long userId, Long targetId) {
        if (userId == null || targetId == null) return false;
        Set<Long> friendSet = cacheClient.getOrLoadSet(
                friendSetKey(userId),
                FRIEND_SET_TTL,
                Long.class,
                () -> friendLinkMapper.listActiveTargetIds(userId)
        );
        return friendSet.contains(targetId);
    }

    public boolean isAllowStrangerConversation(Long userId) {
        if (userId == null) return false;
        UserPrivacySetting setting = cacheClient.getOrLoadById(
                "im:privacy:",
                userId,
                PRIVACY_TTL,
                UserPrivacySetting.class,
                userPrivacySettingMapper::selectById
        );
        if (setting == null) return false;
        return setting.getAllowStrangerChat() != null && setting.getAllowStrangerChat() == 1;
    }

    public void evictFriendSet(Long userId) {
        cacheClient.evict(friendSetKey(userId));
    }

    public void evictBlackSet(Long userId) {
        cacheClient.evict(blackSetKey(userId));
    }

    public void evictPrivacy(Long userId) {
        cacheClient.evict("im:privacy:" + userId);
    }

    private String friendSetKey(Long userId) {
        return "im:friend:set:" + userId;
    }

    private String blackSetKey(Long userId) {
        return "im:black:set:" + userId;
    }
}
