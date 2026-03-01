package com.ecamt35.messageservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("group_join_apply")
@Data
public class GroupJoinApply {
    private Long id;
    private Long groupId;
    private Long applicantId;
    private Integer status;
    private String applyMessage;
    private Long decisionUserId;
    private Date decisionTime;
    private Date createTime;
    private Date updateTime;
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
