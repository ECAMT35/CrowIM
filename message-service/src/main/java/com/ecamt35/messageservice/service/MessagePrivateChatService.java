package com.ecamt35.messageservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecamt35.messageservice.model.entity.MessagePrivateChat;

import java.util.List;

/**
 * (MessagePrivateChat)表服务接口
 *
 * @author ECAMT
 * @since 2025-08-21 19:30:35
 */
public interface MessagePrivateChatService extends IService<MessagePrivateChat> {
    int updateStatusDelivered(Long messagePrivateChatId, Long receiverId);

    int updateStatusRead(Long messagePrivateChatId, Long receiverId);

    int updateStatusFinished(Long messagePrivateChatId, Long senderId);

    List<Long> getReadByReceiverId(Long senderId, Long receiverId);
}
