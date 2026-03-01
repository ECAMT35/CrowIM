package com.ecamt35.messageservice.service;

import cn.hutool.core.lang.Snowflake;
import com.ecamt35.messageservice.constant.RelationStatusConstant;
import com.ecamt35.messageservice.mapper.ConversationMemberMapper;
import com.ecamt35.messageservice.mapper.ImGroupMapper;
import com.ecamt35.messageservice.mapper.MessageMapper;
import com.ecamt35.messageservice.model.bo.SendMessageBo;
import com.ecamt35.messageservice.model.entity.Conversation;
import com.ecamt35.messageservice.model.entity.ConversationMember;
import com.ecamt35.messageservice.model.entity.ImGroup;
import com.ecamt35.messageservice.model.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageCommandService {

    private final Snowflake snowflake;
    private final CursorService cursorService;
    private final ConversationService conversationService;
    private final ConversationMemberMapper memberMapper;
    private final MessageMapper messageMapper;
    private final DeliveryService deliveryService;
    private final ImGroupMapper imGroupMapper;

    /**
     * 发送消息，获取会话ID（单聊创建/群聊传入）、分配 seq、持久化消息（幂等）、并投递给在线成员设备。
     *
     * @param senderId       发送者用户ID
     * @param receiverId     单聊接收者用户ID（chatType=0时必须）
     * @param conversationId 群聊会话ID（chatType=1时必须）
     * @param clientMsgId    客户端消息ID（发送端幂等）
     * @param chatType       0=private，1=group
     * @param msgType        消息类型（文本/文件等）
     * @param content        消息内容
     * @return 消息实体（包含 messageId/convId/seq/sendTime）
     */
    public Message sendMessage(Long senderId,
                               Long receiverId,
                               Long conversationId,
                               Long clientMsgId,
                               Integer chatType,
                               Integer msgType,
                               String content) {

        if (senderId == null || clientMsgId == null || chatType == null || msgType == null) {
            throw new IllegalArgumentException("missing required fields");
        }
        if (chatType != 0 && chatType != 1) {
            throw new IllegalArgumentException("invalid chatType");
        }
        if (content == null) content = "";

        // 先做幂等判定，存在则直接返回，不要先 INCR seq 导致 lastSeq 虚增
        Message existed = messageMapper.findByClientMsgId(clientMsgId, senderId);
        if (existed != null) {
            log.warn("Duplicate client message detected, return existing, clientMsgId:{}", clientMsgId);
            return existed;
        }

        Long convId;
        Conversation conv = null;
        if (chatType == 0) {
            // private
            if (receiverId == null) {
                throw new IllegalArgumentException("receiverId required for private");
            }
            // 必须存在会话双方成员
            convId = conversationService.getOrCreatePrivateConversationIdOrThrow(senderId, receiverId);
        } else {
            // chatType == 1 group
            if (conversationId == null) {
                throw new IllegalArgumentException("conversationId required for group");
            }
            convId = conversationId;

            conv = conversationService.selectById(convId);
            if (conv == null || conv.getType() == null || conv.getType() != 1 || conv.getGroupId() == null) {
                throw new IllegalArgumentException("invalid group conversation");
            }
            // 判断发送者是否有加入群组
            ConversationMember senderCM = memberMapper.findActive(convId, senderId);
            if (senderCM == null) throw new IllegalStateException("not a group member");

            // 群个人禁言优先于后续全员禁言判断
            Long bannedUntil = senderCM.getSpeakBannedUntil();
            if (bannedUntil != null && bannedUntil > System.currentTimeMillis()) {
                throw new IllegalStateException("you are muted in this group");
            }

            ImGroup group = imGroupMapper.selectById(conv.getGroupId());
            if (group == null || group.getDeleted() != null && group.getDeleted() == 1) {
                throw new IllegalStateException("group not found");
            }
            if (group.getMuteAll() != null
                    && group.getMuteAll() == 1
                    && (senderCM.getRole() == null || senderCM.getRole() < RelationStatusConstant.ROLE_ADMIN)) {
                // 全员禁言下仅管理员/群主可发言
                throw new IllegalStateException("group is muted for all members");
            }
        }

        long seq = cursorService.nextSeq(convId);
        long id = snowflake.nextId();
        long sendTime = System.currentTimeMillis();

        Message msg = new Message();
        msg.setId(id);
        msg.setClientMsgId(clientMsgId);
        msg.setConversationId(convId);
        msg.setSeq(seq);
        msg.setSenderId(senderId);
        msg.setMsgType(msgType);
        msg.setContent(content);
        msg.setSendTime(sendTime);

        try {
            messageMapper.insert(msg);
            // todo 发送了消息，考虑是否更新这个会话的ReadSeq为LastSeq
            log.info("Message persisted successfully, clientMsgId:{}, id:{}", clientMsgId, id);
        } catch (DuplicateKeyException e) {
            // 极小概率并发 线程同时通过 existed=null 检查
            log.info("Duplicate message detected, return existing, clientMsgId:{}", clientMsgId);
            Message old = messageMapper.findByClientMsgId(clientMsgId, senderId);
            if (old != null) return old;
            throw e;
        }

        // 投递前拿成员列表
        // todo 群聊后续可优化只投在线成员
        Long groupId = null;
        if (conv != null) {
            groupId = conv.getGroupId();
        }
        List<ConversationMember> members = memberMapper.listActiveMembers(convId);
        for (ConversationMember m : members) {
            Long uid = m.getUserId();
            if (uid == null) continue;
            if (uid.equals(senderId)) continue;

            SendMessageBo bo = new SendMessageBo(
                    uid,
                    content,
                    chatType,
                    msgType,
                    senderId,
                    msg.getId(),
                    msg.getSendTime(),
                    null,
                    convId,
                    groupId,
                    seq
            );
            deliveryService.deliverToUserDevices(bo);
        }

        return msg;
    }
}
