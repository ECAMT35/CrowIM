package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.FriendApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

@Mapper
public interface FriendApplyMapper extends BaseMapper<FriendApply> {

    @Select("""
            select id,applicant_id,target_id,status,apply_message,decision_user_id,decision_time,create_time,update_time,deleted
            from friend_apply
            where applicant_id=#{applicantId} and target_id=#{targetId} and deleted=0
            order by id desc
            limit 1
            """)
    FriendApply findLatestActive(@Param("applicantId") Long applicantId, @Param("targetId") Long targetId);

    @Update("""
            update friend_apply
            set status=#{status}, decision_user_id=#{decisionUserId}, decision_time=#{decisionTime}
            where id=#{applyId} and status=#{expectStatus} and deleted=0
            """)
    int updateDecisionIfStatus(@Param("applyId") Long applyId,
                               @Param("status") Integer status,
                               @Param("decisionUserId") Long decisionUserId,
                               @Param("decisionTime") Date decisionTime,
                               @Param("expectStatus") Integer expectStatus);

    List<FriendApply> listByTarget(@Param("targetId") Long targetId,
                                   @Param("status") Integer status,
                                   @Param("limit") Integer limit,
                                   @Param("offset") Integer offset);
}
