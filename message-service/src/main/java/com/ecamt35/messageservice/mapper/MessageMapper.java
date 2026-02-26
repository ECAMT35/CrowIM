package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("""
            select id, send_time, seq, conversation_id
            from message
            where client_msg_id=#{clientMsgId} and sender_id=#{senderId}
            """)
    Message findByClientMsgId(@Param("clientMsgId") Long clientMsgId, @Param("senderId") Long senderId);

    @Select("""
            select max(seq)
            from message
            where conversation_id=#{convId} and deleted=0
            """)
    Long findMaxSeqByConvId(@Param("convId") Long convId);
}