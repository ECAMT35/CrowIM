package com.ecamt35.userservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("user")
@Data
public class User {
    /**
     *
     */
    private Long id;
    /**
     *
     */
    private String userName;
    /**
     *
     */
    private String email;
    /**
     *
     */
    private String password;
    /**
     *
     */
    private Integer roleId;
    /**
     *
     */
    private Date createTime;
    /**
     *
     */
    private Date updateTime;
    /**
     * 逻辑删除, 1为是，0为否
     */
    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;

}
