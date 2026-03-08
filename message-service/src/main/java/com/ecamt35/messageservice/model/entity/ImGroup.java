package com.ecamt35.messageservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("im_group")
@Data
public class ImGroup {
    private Long id;
    private Long ownerId;
    private String name;
    private String avatar;
    private String notice;
    private Integer joinPolicy;
    private Integer muteAll;
    private Date createTime;
    private Date updateTime;
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
