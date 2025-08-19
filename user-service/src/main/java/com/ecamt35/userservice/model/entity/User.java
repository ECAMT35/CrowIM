package com.ecamt35.userservice.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@TableName("user")
@Data
public class User {
    /**
     *
     */
    @TableId(value = "id")
    private Integer id;
    /**
     *
     */
    @TableField(value = "user_name")
    private String userName;
    /**
     *
     */
    @TableField(value = "email")
    private String email;
    /**
     *
     */
    @TableField(value = "password")
    private String password;
    /**
     *
     */
    @TableField(value = "role_id")
    private Integer roleId;
    /**
     *
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;
    /**
     *
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    /**
     * 逻辑删除, 1为是，0为否
     */
    @TableLogic
    @TableField(value = "is_deleted")
    private Integer isDeleted;

}
