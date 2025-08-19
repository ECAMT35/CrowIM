package com.ecamt35.userservice.model.dto;

import lombok.Data;

@Data
public class UpdateIKDto {
    private String identityKey;
    private String signedPreKey;
}
