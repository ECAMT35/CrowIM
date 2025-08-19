package com.ecamt35.userservice.util;

public enum BusinessErrorCodeEnum {

    FORMAT_ERROR(400, "格式错误"),
    SIGNUP_USERNAME_ERROR(400, "用户名已被注册"),
    SIGNUP_EMAIL_ERROR(400, "邮箱已被注册"),
    SIGNUP_CAPTCHA_ERROR(400, "验证码校验错误，请重新获取填写"),
    NOT_FOUND(404, "资源未找到"),
    PRECONDITION_FAILED(412, "先决条件错误"),
    RATE_LIMITER_ERROR(429, "请求过于频繁,超过限流"),
    SYSTEM_ERROR(503, "系统繁忙"),

    ;

    private final String message;
    private final int httpCode;

    BusinessErrorCodeEnum(int code, String message) {
        this.httpCode = code;
        this.message = message;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public String getMessage() {
        return message;
    }
}