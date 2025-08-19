package com.ecamt35.userservice.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * (UserSignedPreKey)实体类
 *
 * @author ECAMT
 * @since 2025-08-17 18:19:42
 */
@TableName("user_signed_pre_key")
@Data
public class UserSignedPreKey {

    @TableId(value = "id")
    private Integer id;

    @TableField(value = "user_id")
    private Integer userId;

    @TableField(value = "public_key")
    private String publicKey;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
