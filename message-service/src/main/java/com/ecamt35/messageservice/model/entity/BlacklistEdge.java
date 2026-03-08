package com.ecamt35.messageservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("blacklist_edge")
@Data
public class BlacklistEdge {
    private Long id;
    private Long userId;
    private Long targetId;
    private Date createTime;
    private Date updateTime;
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
