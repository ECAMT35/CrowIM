package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.ConversationMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConversationMemberMapper extends BaseMapper<ConversationMember> {

    @Select("""
            select id,conversation_id,user_id,role,mute,last_read_seq,speak_banned_until,join_time,update_time,deleted
            from conversation_member
            where conversation_id=#{convId} and deleted=0
            """)
    List<ConversationMember> listActiveMembers(@Param("convId") Long convId);

    @Select("""
            select id,conversation_id,user_id,role,mute,last_read_seq,speak_banned_until,join_time,update_time,deleted
            from conversation_member
            where conversation_id=#{convId} and user_id=#{userId}
            order by deleted asc
            limit 1
            """)
    ConversationMember findAny(@Param("convId") Long convId, @Param("userId") Long userId);

    @Select("""
            select id,conversation_id,user_id,role,mute,last_read_seq,speak_banned_until,join_time,update_time,deleted
            from conversation_member
            where conversation_id=#{convId} and user_id=#{userId} and deleted=0
            """)
    ConversationMember findActive(@Param("convId") Long convId, @Param("userId") Long userId);

    @Select("""
            select user_id
            from conversation_member
            where conversation_id=#{convId} and role>=2 and deleted=0
            """)
    List<Long> listActiveManagerIds(@Param("convId") Long convId);

    @Update("""
            update conversation_member
            set last_read_seq = greatest(last_read_seq, #{readSeq})
            where conversation_id=#{convId} and user_id=#{userId} and deleted=0
            """)
    int updateReadCursor(@Param("convId") Long convId, @Param("userId") Long userId, @Param("readSeq") Long readSeq);

    @Update("""
            update conversation_member
            set role=#{role}
            where conversation_id=#{convId} and user_id=#{userId} and deleted=0
            """)
    int updateRole(@Param("convId") Long convId, @Param("userId") Long userId, @Param("role") Integer role);

    @Update("""
            update conversation_member
            set speak_banned_until=#{bannedUntil}
            where conversation_id=#{convId} and user_id=#{userId} and deleted=0
            """)
    int updateSpeakBannedUntil(@Param("convId") Long convId, @Param("userId") Long userId, @Param("bannedUntil") Long bannedUntil);

    @Update("""
            update conversation_member
            set deleted=1
            where conversation_id=#{convId} and user_id=#{userId} and deleted=0
            """)
    int removeMember(@Param("convId") Long convId, @Param("userId") Long userId);

    @Update("""
            update conversation_member
            set deleted=1
            where conversation_id=#{convId} and deleted=0
            """)
    int removeAllByConversation(@Param("convId") Long convId);

    @Update("""
            update conversation_member
            set deleted=0, role=#{role}, speak_banned_until=0
            where id=#{id} and deleted=1
            """)
    int restoreDeletedById(@Param("id") Long id, @Param("role") Integer role);

    /**
     * 批量查询用户在多个会话中的已读游标，减少 SUMMARY 场景逐会话查询压力。
     */
    List<Map<String, Object>> batchFindReadSeqByUserAndConvIds(@Param("userId") Long userId, @Param("convIds") List<Long> convIds);
}
