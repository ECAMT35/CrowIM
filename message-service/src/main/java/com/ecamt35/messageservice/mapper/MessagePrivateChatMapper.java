package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.MessagePrivateChat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

/**
 * (MessagePrivateChat)数据库访问层
 *
 * @author ECAMT
 * @since 2025-08-21 19:30:42
 */
@Mapper
public interface MessagePrivateChatMapper extends BaseMapper<MessagePrivateChat> {

    int updateStatusToReadBatch(@Param("ids") Set<Long> ids, @Param("userId") Long userId);

    @Select("select id,send_time from message_private_chat where client_msg_id=#{msgId} and sender_id=#{senderId}")
    MessagePrivateChat findByClientMsgId(Long msgId, Long senderId);

}
