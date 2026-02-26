package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    @Select("""
            select id,type,peer_a,peer_b,group_id,create_time,update_time,deleted
            from conversation
            where type=0 and peer_a=#{a} and peer_b=#{b} and deleted=0
            """)
    Conversation findPrivate(@Param("a") Long a, @Param("b") Long b);

    @Select("""
            select id
            from conversation
            where deleted=0 and (
                (type=0 and (peer_a=#{userId} or peer_b=#{userId}))
                or
                (type=1 and id in (
                    select conversation_id from conversation_member
                    where user_id=#{userId} and deleted=0)
                )
            )
            """)
    List<Long> listConversationIdsForUser(@Param("userId") Long userId);
}