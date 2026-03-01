package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.BlacklistEdge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Set;

@Mapper
public interface BlacklistEdgeMapper extends BaseMapper<BlacklistEdge> {

    @Select("""
            select id,user_id,target_id,create_time,update_time,deleted
            from blacklist_edge
            where user_id=#{userId} and target_id=#{targetId}
            order by deleted asc
            limit 1
            """)
    BlacklistEdge findAny(@Param("userId") Long userId, @Param("targetId") Long targetId);

    @Select("""
            select target_id
            from blacklist_edge
            where user_id=#{userId} and deleted=0
            """)
    Set<Long> listActiveTargetIds(@Param("userId") Long userId);

    @Update("""
            update blacklist_edge
            set deleted=1
            where user_id=#{userId} and target_id=#{targetId} and deleted=0
            """)
    int removeActive(@Param("userId") Long userId, @Param("targetId") Long targetId);

    @Update("""
            update blacklist_edge
            set deleted=0
            where id=#{id} and deleted=1
            """)
    int restoreDeletedById(@Param("id") Long id);
}
