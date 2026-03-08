package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

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

    /**
     * 获取单聊的消息
     */
    @Select("""
            select id, client_msg_id, conversation_id, seq,
                   sender_id, msg_type, content, send_time,
                   create_time, update_time
            from message
            where conversation_id=#{convId}
              and deleted=0
              and seq > #{afterSeq}
              and seq <= #{upperBoundSeq}
            order by seq asc
            limit #{limit}
            """)
    List<Message> listBySeqRange(@Param("convId") Long convId,
                                 @Param("afterSeq") Long afterSeq,
                                 @Param("upperBoundSeq") Long upperBoundSeq,
                                 @Param("limit") Integer limit);

    /**
     * 批量查询会话的最大 seq，减少 SUMMARY 场景逐会话查询压力。
     */
    List<Map<String, Object>> batchFindMaxSeqByConvIds(@Param("convIds") List<Long> convIds);
}
