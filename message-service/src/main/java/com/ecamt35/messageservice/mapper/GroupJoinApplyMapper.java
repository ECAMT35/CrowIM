package com.ecamt35.messageservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.messageservice.model.entity.GroupJoinApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

@Mapper
public interface GroupJoinApplyMapper extends BaseMapper<GroupJoinApply> {

    @Select("""
            select id,group_id,applicant_id,status,apply_message,decision_user_id,decision_time,create_time,update_time,deleted
            from group_join_apply
            where group_id=#{groupId} and applicant_id=#{applicantId} and deleted=0
            order by id desc
            limit 1
            """)
    GroupJoinApply findLatestActive(@Param("groupId") Long groupId, @Param("applicantId") Long applicantId);

    @Update("""
            update group_join_apply
            set status=#{status}, decision_user_id=#{decisionUserId}, decision_time=#{decisionTime}
            where id=#{applyId} and status=#{expectStatus} and deleted=0
            """)
    int updateDecisionIfStatus(@Param("applyId") Long applyId,
                               @Param("status") Integer status,
                               @Param("decisionUserId") Long decisionUserId,
                               @Param("decisionTime") Date decisionTime,
                               @Param("expectStatus") Integer expectStatus);

    List<GroupJoinApply> listForManager(@Param("userId") Long userId,
                                        @Param("groupId") Long groupId,
                                        @Param("status") Integer status,
                                        @Param("limit") Integer limit,
                                        @Param("offset") Integer offset);
}
