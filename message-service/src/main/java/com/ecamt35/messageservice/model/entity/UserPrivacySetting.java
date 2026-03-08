package com.ecamt35.messageservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("user_privacy_setting")
@Data
public class UserPrivacySetting {
    @TableId("user_id")
    private Long userId;
    private Integer allowStrangerChat;
    private Date createTime;
    private Date updateTime;
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
