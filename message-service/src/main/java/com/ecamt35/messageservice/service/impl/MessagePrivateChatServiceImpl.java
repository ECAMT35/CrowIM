package com.ecamt35.messageservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecamt35.messageservice.mapper.MessagePrivateChatMapper;
import com.ecamt35.messageservice.model.entity.MessagePrivateChat;
import com.ecamt35.messageservice.service.MessagePrivateChatService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * (MessagePrivateChat)表服务实现类
 *
 * @author ECAMT
 * @since 2025-08-21 19:30:40
 */
@Service
public class MessagePrivateChatServiceImpl extends ServiceImpl<MessagePrivateChatMapper, MessagePrivateChat> implements MessagePrivateChatService {

    @Resource
    private MessagePrivateChatMapper messagePrivateChatMapper;

    @Override
    public int updateStatusToReadBatch(Set<Long> messagePrivateChatIds, Long userId) {
        return messagePrivateChatMapper.updateStatusToReadBatch(messagePrivateChatIds, userId);
    }

    @Override
    public MessagePrivateChat findByClientMsgId(Long msgId, Long senderId) {
        return messagePrivateChatMapper.findByClientMsgId(msgId, senderId);
    }
}
