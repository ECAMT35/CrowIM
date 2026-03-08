package com.ecamt35.messageservice.service;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Snowflake;
import com.ecamt35.messageservice.constant.RelationStatusConstant;
import com.ecamt35.messageservice.mapper.ConversationMapper;
import com.ecamt35.messageservice.mapper.ConversationMemberMapper;
import com.ecamt35.messageservice.mapper.GroupJoinApplyMapper;
import com.ecamt35.messageservice.mapper.ImGroupMapper;
import com.ecamt35.messageservice.model.entity.Conversation;
import com.ecamt35.messageservice.model.entity.ConversationMember;
import com.ecamt35.messageservice.model.entity.GroupJoinApply;
import com.ecamt35.messageservice.model.entity.ImGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupRelationService {

    private final Snowflake snowflake;
    private final ImGroupMapper imGroupMapper;
    private final GroupJoinApplyMapper groupJoinApplyMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final ConversationService conversationService;
    private final RelationEventPublisher relationEventPublisher;

    /**
     * 创建群组并初始化会话与群主成员。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupCreate(Long userId, Map<String, Object> payload) {
        String name = trimToNull(Convert.toStr(payload.get("name")));
        if (name == null) {
            throw new IllegalArgumentException("group name is required");
        }

        int joinPolicy = Convert.toInt(payload.get("joinPolicy"), RelationStatusConstant.JOIN_POLICY_APPROVAL);
        if (joinPolicy != RelationStatusConstant.JOIN_POLICY_OPEN
                && joinPolicy != RelationStatusConstant.JOIN_POLICY_APPROVAL) {
            joinPolicy = RelationStatusConstant.JOIN_POLICY_APPROVAL;
        }

        Long groupId = snowflake.nextId();
        ImGroup group = new ImGroup();
        group.setId(groupId);
        group.setOwnerId(userId);
        group.setName(name);
        group.setAvatar(trimToNull(Convert.toStr(payload.get("avatar"))));
        group.setNotice(trimToNull(Convert.toStr(payload.get("notice"))));
        group.setJoinPolicy(joinPolicy);
        group.setMuteAll(0);
        group.setDeleted(0);
        imGroupMapper.insert(group);

        Long convId = snowflake.nextId();
        Conversation conversation = new Conversation();
        conversation.setId(convId);
        conversation.setType(1);
        conversation.setGroupId(groupId);
        conversation.setDeleted(0);
        conversationMapper.insert(conversation);

        ConversationMember owner = new ConversationMember();
        owner.setId(snowflake.nextId());
        owner.setConversationId(convId);
        owner.setUserId(userId);
        owner.setRole(RelationStatusConstant.ROLE_OWNER);
        owner.setMute(0);
        owner.setLastReadSeq(0L);
        owner.setSpeakBannedUntil(0L);
        owner.setDeleted(0);
        conversationMemberMapper.insert(owner);

        conversationService.evictUserConversationIds(userId);

        return Map.of(
                "groupId", groupId,
                "conversationId", convId,
                "joinPolicy", joinPolicy
        );
    }

    /**
     * 获取群资料（仅群成员可读）。
     */
    public Map<String, Object> groupGetProfile(Long userId, Map<String, Object> payload) {
        Long groupId = requireLong(payload, "groupId");
        ImGroup group = requireGroup(groupId);
        Conversation conv = requireGroupConversation(groupId);
        ConversationMember member = requireActiveMember(conv.getId(), userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupId", group.getId());
        result.put("conversationId", conv.getId());
        result.put("name", group.getName());
        result.put("avatar", group.getAvatar());
        result.put("notice", group.getNotice());
        result.put("ownerId", group.getOwnerId());
        result.put("joinPolicy", group.getJoinPolicy());
        result.put("muteAll", Objects.equals(group.getMuteAll(), 1));
        result.put("myRole", member.getRole());
        result.put("isMember", true);
        return result;
    }

    /**
     * 解散群组并清理会话成员关系。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupDismiss(Long userId, Map<String, Object> payload) {
        Long groupId = requireLong(payload, "groupId");
        ImGroup group = requireGroup(groupId);
        if (!Objects.equals(group.getOwnerId(), userId)) {
            throw new IllegalStateException("only owner can dismiss group");
        }
        Conversation conv = requireGroupConversation(groupId);

        List<ConversationMember> members = conversationMemberMapper.listActiveMembers(conv.getId());

        group.setDeleted(1);
        imGroupMapper.updateById(group);

        conv.setDeleted(1);
        conversationMapper.updateById(conv);
        conversationMemberMapper.removeAllByConversation(conv.getId());

        conversationService.evictConversationById(conv.getId());
        for (ConversationMember member : members) {
            if (member.getUserId() != null) {
                conversationService.evictUserConversationIds(member.getUserId());
            }
        }

        relationEventPublisher.emitEvent(
                members.stream().map(ConversationMember::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()),
                "GROUP_DISMISSED",
                Map.of("groupId", groupId, "conversationId", conv.getId())
        );

        return Map.of("dismissed", true);
    }

    /**
     * 更新群资料与可选入群策略。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupUpdateProfile(Long userId, Map<String, Object> payload) {
        Long groupId = requireLong(payload, "groupId");
        Conversation conv = requireGroupConversation(groupId);
        ConversationMember operator = requireActiveMember(conv.getId(), userId);
        if (operator.getRole() == null || operator.getRole() < RelationStatusConstant.ROLE_ADMIN) {
            throw new IllegalStateException("no permission");
        }

        ImGroup group = requireGroup(groupId);
        String name = trimToNull(Convert.toStr(payload.get("name")));
        String avatar = trimToNull(Convert.toStr(payload.get("avatar")));
        String notice = trimToNull(Convert.toStr(payload.get("notice")));
        Integer joinPolicy = Convert.toInt(payload.get("joinPolicy"), null);

        if (name != null) group.setName(name);
        if (avatar != null) group.setAvatar(avatar);
        if (notice != null) group.setNotice(notice);

        if (joinPolicy != null) {
            if (!Objects.equals(operator.getRole(), RelationStatusConstant.ROLE_OWNER)) {
                throw new IllegalStateException("only owner can change joinPolicy");
            }
            if (joinPolicy != RelationStatusConstant.JOIN_POLICY_OPEN
                    && joinPolicy != RelationStatusConstant.JOIN_POLICY_APPROVAL) {
                throw new IllegalArgumentException("invalid joinPolicy");
            }
            group.setJoinPolicy(joinPolicy);
        }

        imGroupMapper.updateById(group);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("groupId", groupId);
        eventData.put("name", group.getName());
        eventData.put("avatar", group.getAvatar());
        eventData.put("notice", group.getNotice());
        eventData.put("joinPolicy", group.getJoinPolicy());
        relationEventPublisher.emitEvent(groupMemberIds(conv.getId()), "GROUP_PROFILE_UPDATED", eventData);

        return Map.of("updated", true);
    }

    /**
     * 普通成员退出群聊。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupQuit(Long userId, Map<String, Object> payload) {
        Long groupId = requireLong(payload, "groupId");
        Conversation conv = requireGroupConversation(groupId);
        ConversationMember member = requireActiveMember(conv.getId(), userId);

        if (Objects.equals(member.getRole(), RelationStatusConstant.ROLE_OWNER)) {
            throw new IllegalStateException("owner cannot quit directly");
        }

        conversationMemberMapper.removeMember(conv.getId(), userId);
        conversationService.evictUserConversationIds(userId);

        relationEventPublisher.emitEvent(groupMemberIds(conv.getId()), "GROUP_MEMBER_QUIT", Map.of(
                "groupId", groupId,
                "userId", userId
        ));

        return Map.of("quit", true);
    }

    /**
     * 发起入群申请或自动入群。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupJoinApply(Long userId, Map<String, Object> payload) {
        Long groupId = requireLong(payload, "groupId");
        String applyMessage = trimToNull(Convert.toStr(payload.get("applyMessage")));

        ImGroup group = requireGroup(groupId);
        Conversation conv = requireGroupConversation(groupId);

        ConversationMember member = conversationMemberMapper.findActive(conv.getId(), userId);
        if (member != null) {
            return Map.of("status", "already_member");
        }

        if (Objects.equals(group.getJoinPolicy(), RelationStatusConstant.JOIN_POLICY_OPEN)) {
            ensureMemberActive(conv.getId(), userId, RelationStatusConstant.ROLE_MEMBER);
            conversationService.evictUserConversationIds(userId);

            relationEventPublisher.emitEvent(groupMemberIds(conv.getId()), "GROUP_MEMBER_JOINED", Map.of(
                    "groupId", groupId,
                    "userId", userId,
                    "auto", true
            ));
            return Map.of("status", "joined");
        }

        GroupJoinApply latest = groupJoinApplyMapper.findLatestActive(groupId, userId);
        if (latest != null && Objects.equals(latest.getStatus(), RelationStatusConstant.APPLY_PENDING)) {
            return Map.of("status", "pending", "applyId", latest.getId());
        }

        GroupJoinApply apply = new GroupJoinApply();
        apply.setId(snowflake.nextId());
        apply.setGroupId(groupId);
        apply.setApplicantId(userId);
        apply.setStatus(RelationStatusConstant.APPLY_PENDING);
        apply.setApplyMessage(applyMessage);
        apply.setDeleted(0);
        groupJoinApplyMapper.insert(apply);

        relationEventPublisher.emitEvent(groupManagerAndOwnerIds(conv.getId()), "GROUP_JOIN_APPLY_CREATED", Map.of(
                "applyId", apply.getId(),
                "groupId", groupId,
                "applicantId", userId
        ));

        return Map.of("status", "pending", "applyId", apply.getId());
    }

    /**
     * 获取群主/管理员可处理的入群申请列表。
     */
    public Map<String, Object> groupJoinApplyList(Long userId, Map<String, Object> payload) {
        Long groupId = Convert.toLong(payload.get("groupId"));
        Integer statusValue = Convert.toInt(payload.get("status"), RelationStatusConstant.APPLY_PENDING);
        Integer status = normalizeApplyStatus(statusValue);

        int pageNo = normalizePageNo(Convert.toInt(payload.get("pageNo"), 1));
        int pageSize = normalizePageSize(Convert.toInt(payload.get("pageSize"), 20));
        int offset = (pageNo - 1) * pageSize;

        List<GroupJoinApply> rows = groupJoinApplyMapper.listForManager(userId, groupId, status, pageSize + 1, offset);
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
     * 审批入群申请。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupJoinDecide(Long userId, Map<String, Object> payload) {
        Long applyId = requireLong(payload, "applyId");
        boolean approve = Convert.toBool(payload.get("approve"), false);

        GroupJoinApply apply = groupJoinApplyMapper.selectById(applyId);
        if (apply == null || apply.getDeleted() != null && apply.getDeleted() == 1) {
            throw new IllegalArgumentException("apply not found");
        }
        if (!Objects.equals(apply.getStatus(), RelationStatusConstant.APPLY_PENDING)) {
            return Map.of("status", "already_decided", "applyId", applyId);
        }

        Conversation conv = requireGroupConversation(apply.getGroupId());
        ConversationMember operator = requireActiveMember(conv.getId(), userId);
        if (operator.getRole() == null || operator.getRole() < RelationStatusConstant.ROLE_ADMIN) {
            throw new IllegalStateException("no permission to approve join apply");
        }

        int decisionStatus = approve ? RelationStatusConstant.APPLY_ACCEPTED : RelationStatusConstant.APPLY_REJECTED;
        Date decisionTime = new Date();
        int updated = groupJoinApplyMapper.updateDecisionIfStatus(
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
            ensureMemberActive(conv.getId(), apply.getApplicantId(), RelationStatusConstant.ROLE_MEMBER);
            conversationService.evictUserConversationIds(apply.getApplicantId());
        }

        // 审批结果通知申请人、群主和管理员，补充审批人信息用于管理端展示
        Set<Long> decisionNotifyUsers = new HashSet<>(groupManagerAndOwnerIds(conv.getId()));
        decisionNotifyUsers.add(apply.getApplicantId());
        relationEventPublisher.emitEvent(decisionNotifyUsers, "GROUP_JOIN_APPLY_DECIDED", Map.of(
                "applyId", applyId,
                "groupId", apply.getGroupId(),
                "applicantId", apply.getApplicantId(),
                "decisionUserId", userId,
                "approve", approve
        ));

        if (approve) {
            relationEventPublisher.emitEvent(groupMemberIds(conv.getId()), "GROUP_MEMBER_JOINED", Map.of(
                    "groupId", apply.getGroupId(),
                    "userId", apply.getApplicantId(),
                    "auto", false
            ));
        }

        return Map.of("status", approve ? "accepted" : "rejected", "applyId", applyId);
    }

    /**
     * 将成员踢出群聊。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupKick(Long userId, Map<String, Object> payload) {
        Long groupId = requireLong(payload, "groupId");
        Long targetUserId = requireLong(payload, "targetUserId");

        Conversation conv = requireGroupConversation(groupId);
        ConversationMember operator = requireActiveMember(conv.getId(), userId);
        ConversationMember target = requireActiveMember(conv.getId(), targetUserId);

        ensureCanManageMember(operator, target);

        conversationMemberMapper.removeMember(conv.getId(), targetUserId);
        conversationService.evictUserConversationIds(targetUserId);

        // 踢人事件仅通知群主/管理员以及被踢成员
        Set<Long> notifyUsers = new HashSet<>(groupManagerAndOwnerIds(conv.getId()));
        notifyUsers.add(targetUserId);
        relationEventPublisher.emitEvent(notifyUsers, "GROUP_MEMBER_KICKED", Map.of(
                "groupId", groupId,
                "operatorId", userId,
                "targetUserId", targetUserId
        ));

        return Map.of("kicked", true);
    }

    /**
     * 设置管理员角色。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupAdminSet(Long userId, Map<String, Object> payload) {
        Long groupId = requireLong(payload, "groupId");
        Long targetUserId = requireLong(payload, "targetUserId");

        Conversation conv = requireGroupConversation(groupId);
        ConversationMember operator = requireActiveMember(conv.getId(), userId);
        if (!Objects.equals(operator.getRole(), RelationStatusConstant.ROLE_OWNER)) {
            throw new IllegalStateException("only owner can set admin");
        }
        ConversationMember target = requireActiveMember(conv.getId(), targetUserId);
        if (Objects.equals(target.getRole(), RelationStatusConstant.ROLE_OWNER)) {
            throw new IllegalStateException("cannot change owner role");
        }

        conversationMemberMapper.updateRole(conv.getId(), targetUserId, RelationStatusConstant.ROLE_ADMIN);

        relationEventPublisher.emitEvent(groupMemberIds(conv.getId()), "GROUP_ADMIN_SET", Map.of(
                "groupId", groupId,
                "targetUserId", targetUserId
        ));

        return Map.of("updated", true);
    }

    /**
     * 取消管理员角色。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupAdminUnset(Long userId, Map<String, Object> payload) {
        Long groupId = requireLong(payload, "groupId");
        Long targetUserId = requireLong(payload, "targetUserId");

        Conversation conv = requireGroupConversation(groupId);
        ConversationMember operator = requireActiveMember(conv.getId(), userId);
        if (!Objects.equals(operator.getRole(), RelationStatusConstant.ROLE_OWNER)) {
            throw new IllegalStateException("only owner can unset admin");
        }
        ConversationMember target = requireActiveMember(conv.getId(), targetUserId);
        if (Objects.equals(target.getRole(), RelationStatusConstant.ROLE_OWNER)) {
            throw new IllegalStateException("cannot change owner role");
        }

        conversationMemberMapper.updateRole(conv.getId(), targetUserId, RelationStatusConstant.ROLE_MEMBER);

        relationEventPublisher.emitEvent(groupMemberIds(conv.getId()), "GROUP_ADMIN_UNSET", Map.of(
                "groupId", groupId,
                "targetUserId", targetUserId
        ));

        return Map.of("updated", true);
    }

    /**
     * 设置或取消全员禁言。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupMuteAll(Long userId, Map<String, Object> payload, boolean muted) {
        Long groupId = requireLong(payload, "groupId");
        Conversation conv = requireGroupConversation(groupId);
        ConversationMember operator = requireActiveMember(conv.getId(), userId);
        if (operator.getRole() == null || operator.getRole() < RelationStatusConstant.ROLE_ADMIN) {
            throw new IllegalStateException("no permission");
        }

        ImGroup group = requireGroup(groupId);
        group.setMuteAll(muted ? 1 : 0);
        imGroupMapper.updateById(group);

        relationEventPublisher.emitEvent(groupMemberIds(conv.getId()), muted ? "GROUP_MUTE_ALL_SET" : "GROUP_MUTE_ALL_UNSET", Map.of(
                "groupId", groupId,
                "muteAll", muted
        ));

        return Map.of("muteAll", muted);
    }

    /**
     * 设置或解除单成员禁言。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> groupMemberMute(Long userId, Map<String, Object> payload, boolean muted) {
        Long groupId = requireLong(payload, "groupId");
        Long targetUserId = requireLong(payload, "targetUserId");
        Conversation conv = requireGroupConversation(groupId);

        ConversationMember operator = requireActiveMember(conv.getId(), userId);
        ConversationMember target = requireActiveMember(conv.getId(), targetUserId);

        ensureCanManageMember(operator, target);

        long bannedUntil = 0L;
        if (muted) {
            Integer durationSeconds = Convert.toInt(payload.get("durationSeconds"), 600);
            if (durationSeconds <= 0) {
                throw new IllegalArgumentException("durationSeconds must be positive");
            }
            bannedUntil = System.currentTimeMillis() + durationSeconds * 1000L;
        }

        conversationMemberMapper.updateSpeakBannedUntil(conv.getId(), targetUserId, bannedUntil);

        relationEventPublisher.emitEvent(Set.of(targetUserId), muted ? "GROUP_MEMBER_MUTED" : "GROUP_MEMBER_MUTE_REMOVED", Map.of(
                "groupId", groupId,
                "targetUserId", targetUserId,
                "bannedUntil", bannedUntil
        ));

        return Map.of("muted", muted, "bannedUntil", bannedUntil);
    }

    private void ensureCanManageMember(ConversationMember operator, ConversationMember target) {
        Integer operatorRole = operator.getRole();
        Integer targetRole = target.getRole();
        if (operatorRole == null || operatorRole < RelationStatusConstant.ROLE_ADMIN) {
            throw new IllegalStateException("no permission");
        }
        if (Objects.equals(targetRole, RelationStatusConstant.ROLE_OWNER)) {
            throw new IllegalStateException("cannot operate owner");
        }
        if (Objects.equals(operatorRole, RelationStatusConstant.ROLE_ADMIN)
                && targetRole != null
                && targetRole >= RelationStatusConstant.ROLE_ADMIN) {
            throw new IllegalStateException("admin cannot operate admin/owner");
        }
    }

    private ImGroup requireGroup(Long groupId) {
        ImGroup group = imGroupMapper.selectById(groupId);
        if (group == null || group.getDeleted() != null && group.getDeleted() == 1) {
            throw new IllegalArgumentException("group not found");
        }
        return group;
    }

    private Conversation requireGroupConversation(Long groupId) {
        Conversation conv = conversationMapper.findGroupConversationByGroupId(groupId);
        if (conv == null || conv.getDeleted() != null && conv.getDeleted() == 1) {
            throw new IllegalArgumentException("group conversation not found");
        }
        return conv;
    }

    private ConversationMember requireActiveMember(Long convId, Long userId) {
        ConversationMember member = conversationMemberMapper.findActive(convId, userId);
        if (member == null) {
            throw new IllegalStateException("not a group member");
        }
        return member;
    }

    private Set<Long> groupMemberIds(Long convId) {
        List<ConversationMember> members = conversationMemberMapper.listActiveMembers(convId);
        if (members == null || members.isEmpty()) return Collections.emptySet();
        return members.stream()
                .map(ConversationMember::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Long> groupManagerAndOwnerIds(Long convId) {
        List<Long> managerIds = conversationMemberMapper.listActiveManagerIds(convId);
        if (managerIds == null || managerIds.isEmpty()) return Collections.emptySet();
        return managerIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private void ensureMemberActive(Long convId, Long userId, int role) {
        ConversationMember any = conversationMemberMapper.findAny(convId, userId);
        if (any != null && any.getDeleted() != null && any.getDeleted() == 0) {
            Integer currentRole = any.getRole();
            if (currentRole == null || role > currentRole) {
                any.setRole(role);
                conversationMemberMapper.updateById(any);
            }
            return;
        }

        if (any != null && any.getDeleted() != null && any.getDeleted() == 1) {
            int restored = conversationMemberMapper.restoreDeletedById(any.getId(), role);
            if (restored == 0 && conversationMemberMapper.findActive(convId, userId) == null) {
                throw new IllegalStateException("restore group member failed");
            }
            return;
        }

        ConversationMember member = new ConversationMember();
        member.setId(snowflake.nextId());
        member.setConversationId(convId);
        member.setUserId(userId);
        member.setRole(role);
        member.setMute(0);
        member.setLastReadSeq(0L);
        member.setSpeakBannedUntil(0L);
        member.setDeleted(0);
        conversationMemberMapper.insert(member);
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
}
