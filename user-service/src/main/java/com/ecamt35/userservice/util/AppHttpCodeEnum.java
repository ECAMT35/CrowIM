package com.ecamt35.userservice.util;

/**
 * 返回码枚举类
 */
public enum AppHttpCodeEnum {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求错误"),
    FREQUENT_REQUEST(429, "请求过于频繁，请稍后再试"),
    FORBIDDEN(403, "拒绝执行"),
    CONFLICT(409, "资源状态存在冲突"),
    PRECONDITION_FAILED(412, "先决条件错误"),
    SYSTEM_ERROR(500, "系统错误");

    final int code;
    final String errorMsg;

    AppHttpCodeEnum(int code, String errorMsg) {
        this.code = code;
        this.errorMsg = errorMsg;
    }

    public int getCode() {
        return code;
    }

    public String getErrorMsg() {
        return errorMsg;
    }
}