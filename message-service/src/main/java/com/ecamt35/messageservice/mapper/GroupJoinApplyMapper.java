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

    @Select("""
            <script>
            select gja.id,gja.group_id,gja.applicant_id,gja.status,gja.apply_message,gja.decision_user_id,gja.decision_time,gja.create_time,gja.update_time,gja.deleted
            from group_join_apply gja
            join conversation c on c.group_id = gja.group_id and c.type = 1 and c.deleted = 0
            join conversation_member cm on cm.conversation_id = c.id and cm.user_id = #{userId} and cm.deleted = 0 and cm.role &gt;= 2
            where gja.deleted = 0
            <if test="groupId != null">
                and gja.group_id = #{groupId}
            </if>
            <if test="status != null">
                and gja.status = #{status}
            </if>
            order by gja.id desc
            limit #{limit} offset #{offset}
            </script>
            """)
    List<GroupJoinApply> listForManager(@Param("userId") Long userId,
                                        @Param("groupId") Long groupId,
                                        @Param("status") Integer status,
                                        @Param("limit") Integer limit,
                                        @Param("offset") Integer offset);
}
