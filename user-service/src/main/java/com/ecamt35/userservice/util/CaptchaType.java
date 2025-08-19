package com.ecamt35.userservice.util;

import com.ecamt35.userservice.constant.CaptchaCacheConstant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CaptchaType {
    REGISTER("注册账号", CaptchaCacheConstant.SIGNUP_MAIL_PREFIX),
    FORGET("重置密码", CaptchaCacheConstant.FORGET_MAIL_PREFIX);

    final String subject;
    final String redisKey;
}
