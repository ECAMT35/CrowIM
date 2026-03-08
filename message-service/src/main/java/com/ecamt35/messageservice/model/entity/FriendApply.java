package com.ecamt35.messageservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("friend_apply")
@Data
public class FriendApply {
    private Long id;
    private Long applicantId;
    private Long targetId;
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
