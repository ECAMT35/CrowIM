package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.MessagePrivateChat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * (MessagePrivateChat)数据库访问层
 *
 * @author ECAMT
 * @since 2025-08-21 19:30:42
 */
@Mapper
public interface MessagePrivateChatMapper extends BaseMapper<MessagePrivateChat> {

    @Update("update message_private_chat set status=2, push_time=#{pushTime} where id=#{messagePrivateChatId} and receiver_id=#{receiverId} and status=1")
    int updateStatusDelivered(Long messagePrivateChatId, Long receiverId, Long pushTime);

    @Update("update message_private_chat set status=3 where id=#{messagePrivateChatId} and receiver_id=#{receiverId} and status=2")
    int updateStatusRead(Long messagePrivateChatId, Long receiverId);

    @Update("update message_private_chat set status=4 where id=#{messagePrivateChatId} and sender_id=#{senderId} and status=3")
    int updateStatusFinished(Long messagePrivateChatId, Long senderId);

    @Select("select id from message_private_chat where sender_id=#{senderId} and receiver_id=#{receiverId} and status=3")
    List<Long> getReadByReceiverId(Long senderId, Long receiverId);
}

