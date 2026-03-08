package com.ecamt35.messageservice.service;

import com.ecamt35.messageservice.constant.MessageDispatchConstant;
import com.ecamt35.messageservice.mapper.ConversationMemberMapper;
import com.ecamt35.messageservice.model.bo.MessageDispatchBo;
import com.ecamt35.messageservice.model.bo.SendMessageBo;
import com.ecamt35.messageservice.model.entity.ConversationMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息分发编排服务：
 * 1) 发布异步分发任务
 * 2) 消费后执行成员遍历投递
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDispatchService {

    private final RabbitTemplate rabbitTemplate;
    private final MessageDispatchConstant messageDispatchConstant;
    private final ConversationMemberMapper memberMapper;
    private final DeliveryService deliveryService;

    /**
     * 发布异步分发任务。
     *
     * @param dispatchBo 分发任务
     */
    public void publishDispatchTask(MessageDispatchBo dispatchBo) {
        if (dispatchBo == null || dispatchBo.getMessageId() == null || dispatchBo.getConversationId() == null) {
            throw new IllegalArgumentException("invalid dispatch task");
        }
        rabbitTemplate.convertAndSend(
                messageDispatchConstant.getExchange(),
                messageDispatchConstant.getRoutingKey(),
                dispatchBo
        );
    }

    /**
     * 执行分发任务：查询会话成员并逐成员投递。
     *
     * @param dispatchBo 分发任务
     */
    public void handleDispatch(MessageDispatchBo dispatchBo) {
        if (dispatchBo == null) {
            log.warn("Skip empty message dispatch task");
            return;
        }
        Long convId = dispatchBo.getConversationId();
        Long senderId = dispatchBo.getSenderId();
        if (convId == null || senderId == null) {
            log.warn("Skip invalid message dispatch task, messageId={}, conversationId={}, senderId={}",
                    dispatchBo.getMessageId(), convId, senderId);
            return;
        }

        // todo 群成员缓存
        // 根据会话成员进行实际投递，发送者自己不回推。
        List<ConversationMember> members = memberMapper.listActiveMembers(convId);
        if (members == null || members.isEmpty()) {
            return;
        }
        for (ConversationMember m : members) {
            Long uid = m.getUserId();
            if (uid == null || uid.equals(senderId)) {
                continue;
            }

            SendMessageBo bo = new SendMessageBo(
                    uid,
                    dispatchBo.getContent(),
                    dispatchBo.getChatType(),
                    dispatchBo.getMsgType(),
                    senderId,
                    dispatchBo.getMessageId(),
                    dispatchBo.getSendTime(),
                    null,
                    convId,
                    dispatchBo.getGroupId(),
                    dispatchBo.getSeq()
            );
            try {
                deliveryService.deliverToUserDevices(bo);
            } catch (Exception ex) {
                log.error("Deliver message failed, messageId={}, conversationId={}, targetUserId={}",
                        dispatchBo.getMessageId(), convId, uid, ex);
            }
        }
    }
}
