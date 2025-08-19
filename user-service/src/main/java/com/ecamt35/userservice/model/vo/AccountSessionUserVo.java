package com.ecamt35.userservice.model.vo;

import lombok.Data;

import java.util.Date;

@Data
public class AccountSessionUserVo {
    private Integer id;

    private String userName;

    private String email;

    private Integer roleId;

    private Date createTime;
}
