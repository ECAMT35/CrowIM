package com.ecamt35.messageservice.service;

import com.ecamt35.messageservice.constant.RelationOpConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RelationDomainService {

    private final FriendRelationService friendRelationService;
    private final BlacklistRelationService blacklistRelationService;
    private final PrivacyRelationService privacyRelationService;
    private final GroupRelationService groupRelationService;

    /**
     * 关系域统一入口：按操作码路由到对应业务服务。
     */
    public Object execute(Long userId, String op, Map<String, Object> payload) {
        if (op == null || op.isBlank()) {
            throw new IllegalArgumentException("op is required");
        }
        Map<String, Object> safePayload = payload == null ? Collections.emptyMap() : payload;

        return switch (op) {
            case RelationOpConstant.FRIEND_APPLY -> friendRelationService.friendApply(userId, safePayload);
            case RelationOpConstant.FRIEND_APPLY_DECIDE -> friendRelationService.friendApplyDecide(userId, safePayload);
            case RelationOpConstant.FRIEND_APPLY_LIST -> friendRelationService.friendApplyList(userId, safePayload);
            case RelationOpConstant.FRIEND_LIST -> friendRelationService.friendList(userId);
            case RelationOpConstant.FRIEND_DELETE -> friendRelationService.friendDelete(userId, safePayload);

            case RelationOpConstant.BLACKLIST_ADD -> blacklistRelationService.blacklistAdd(userId, safePayload);
            case RelationOpConstant.BLACKLIST_REMOVE -> blacklistRelationService.blacklistRemove(userId, safePayload);
            case RelationOpConstant.BLACKLIST_LIST -> blacklistRelationService.blacklistList(userId);

            case RelationOpConstant.PRIVACY_SET_STRANGER_CHAT -> privacyRelationService.privacySetStrangerChat(userId, safePayload);
            case RelationOpConstant.PRIVACY_GET_SETTINGS -> privacyRelationService.privacyGetSettings(userId);

            case RelationOpConstant.GROUP_CREATE -> groupRelationService.groupCreate(userId, safePayload);
            case RelationOpConstant.GROUP_GET_PROFILE -> groupRelationService.groupGetProfile(userId, safePayload);
            case RelationOpConstant.GROUP_DISMISS -> groupRelationService.groupDismiss(userId, safePayload);
            case RelationOpConstant.GROUP_UPDATE_PROFILE -> groupRelationService.groupUpdateProfile(userId, safePayload);
            case RelationOpConstant.GROUP_QUIT -> groupRelationService.groupQuit(userId, safePayload);
            case RelationOpConstant.GROUP_JOIN_APPLY -> groupRelationService.groupJoinApply(userId, safePayload);
            case RelationOpConstant.GROUP_JOIN_DECIDE -> groupRelationService.groupJoinDecide(userId, safePayload);
            case RelationOpConstant.GROUP_JOIN_APPLY_LIST -> groupRelationService.groupJoinApplyList(userId, safePayload);
            case RelationOpConstant.GROUP_KICK -> groupRelationService.groupKick(userId, safePayload);
            case RelationOpConstant.GROUP_ADMIN_SET -> groupRelationService.groupAdminSet(userId, safePayload);
            case RelationOpConstant.GROUP_ADMIN_UNSET -> groupRelationService.groupAdminUnset(userId, safePayload);
            case RelationOpConstant.GROUP_MUTE_ALL_SET -> groupRelationService.groupMuteAll(userId, safePayload, true);
            case RelationOpConstant.GROUP_MUTE_ALL_UNSET -> groupRelationService.groupMuteAll(userId, safePayload, false);
            case RelationOpConstant.GROUP_MEMBER_MUTE_SET -> groupRelationService.groupMemberMute(userId, safePayload, true);
            case RelationOpConstant.GROUP_MEMBER_MUTE_UNSET -> groupRelationService.groupMemberMute(userId, safePayload, false);
            default -> throw new IllegalArgumentException("unsupported op: " + op);
        };
    }
}
