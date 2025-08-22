package com.ecamt35.messageservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecamt35.messageservice.mapper.MessagePrivateChatMapper;
import com.ecamt35.messageservice.model.entity.MessagePrivateChat;
import com.ecamt35.messageservice.service.MessagePrivateChatService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public int updateStatusDelivered(Long messagePrivateChatId, Long receiverId) {
        Long pushTime = System.currentTimeMillis();
        return messagePrivateChatMapper.updateStatusDelivered(messagePrivateChatId, receiverId, pushTime);
    }

    @Override
    public int updateStatusRead(Long messagePrivateChatId, Long receiverId) {
        return messagePrivateChatMapper.updateStatusRead(messagePrivateChatId, receiverId);
    }

    @Override
    public int updateStatusFinished(Long messagePrivateChatId, Long senderId) {
        return messagePrivateChatMapper.updateStatusFinished(messagePrivateChatId, senderId);
    }

    @Override
    public List<Long> getReadByReceiverId(Long senderId, Long receiverId) {
        return messagePrivateChatMapper.getReadByReceiverId(senderId, receiverId);
    }
}
