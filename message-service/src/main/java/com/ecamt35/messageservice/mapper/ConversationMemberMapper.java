package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.ConversationMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {

    @Select("""
            select id,conversation_id,user_id,role,mute,last_read_seq,join_time,update_time,deleted
            from conversation_member
            where conversation_id=#{convId} and deleted=0
            """)
    List<ConversationMember> listActiveMembers(@Param("convId") Long convId);

    @Select("""
            select id,conversation_id,user_id,role,mute,last_read_seq,join_time,update_time,deleted
            from conversation_member
            where conversation_id=#{convId} and user_id=#{userId}
            order by deleted asc
            limit 1
            """)
    ConversationMember findAny(@Param("convId") Long convId, @Param("userId") Long userId);

    @Select("""
            select id,conversation_id,user_id,role,mute,last_read_seq,join_time,update_time,deleted
            from conversation_member
            where conversation_id=#{convId} and user_id=#{userId} and deleted=0
            """)
    ConversationMember findActive(@Param("convId") Long convId, @Param("userId") Long userId);

    @Update("""
            update conversation_member
            set last_read_seq = greatest(last_read_seq, #{readSeq})
            where conversation_id=#{convId} and user_id=#{userId} and deleted=0
            """)
    int updateReadCursor(@Param("convId") Long convId, @Param("userId") Long userId, @Param("readSeq") Long readSeq);
}