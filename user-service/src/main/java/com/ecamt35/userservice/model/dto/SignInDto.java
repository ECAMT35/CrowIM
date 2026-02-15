package com.ecamt35.userservice.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SignInDto {

    private String userName;

    private String email;

    private String password;

    private String deviceId;

    @Builder.Default
    private boolean rememberMe = true;
}
