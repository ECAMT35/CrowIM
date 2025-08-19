package com.ecamt35.userservice.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * (UserOneTimePreKey)实体类
 *
 * @author ECAMT
 * @since 2025-08-17 18:14:36
 */
@TableName("user_one_time_pre_key")
@Data
public class UserOneTimePreKey {

    @TableId(value = "id")
    private Integer id;

    @TableField(value = "user_id")
    private Integer userId;

    @TableField(value = "public_key")
    private String publicKey;
    /**
     * 1=已使用，0=未使用
     */
    @TableField(value = "is_used")
    private Integer isUsed;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
