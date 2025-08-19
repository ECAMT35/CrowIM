package com.ecamt35.userservice.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class SignUpDto {

    private String userName;

    private String email;

    private String password;

    private String captcha;

    private String identityKey;
    private String signedPreKey;
    private List<String> oneTimePreKey;
}
