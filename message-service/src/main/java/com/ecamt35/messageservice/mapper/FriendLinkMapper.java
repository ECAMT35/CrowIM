package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.FriendLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Set;

@Mapper
public interface FriendLinkMapper extends BaseMapper<FriendLink> {

    @Select("""
            select id,user_id,target_id,create_time,update_time,deleted
            from friend_link
            where user_id=#{userId} and target_id=#{targetId}
            order by deleted asc
            limit 1
            """)
    FriendLink findAny(@Param("userId") Long userId, @Param("targetId") Long targetId);

    @Select("""
            select count(1) > 0
            from friend_link
            where user_id=#{userId} and target_id=#{targetId} and deleted=0
            """)
    boolean existsActive(@Param("userId") Long userId, @Param("targetId") Long targetId);

    @Select("""
            select target_id
            from friend_link
            where user_id=#{userId} and deleted=0
            """)
    Set<Long> listActiveTargetIds(@Param("userId") Long userId);

    @Update("""
            update friend_link
            set deleted=1
            where user_id=#{userId} and target_id=#{targetId} and deleted=0
            """)
    int removeActive(@Param("userId") Long userId, @Param("targetId") Long targetId);

    @Update("""
            update friend_link
            set deleted=0
            where id=#{id} and deleted=1
            """)
    int restoreDeletedById(@Param("id") Long id);
}
