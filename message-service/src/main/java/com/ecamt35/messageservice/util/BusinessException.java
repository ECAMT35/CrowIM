package com.ecamt35.messageservice.util;

import java.io.Serial;

/**
 * 业务异常类，用于封装业务层的异常信息
 * 继承RuntimeException，无需强制捕获，符合业务异常的使用场景
 */
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BusinessErrorCodeEnum businessErrorCode;

    public BusinessException(BusinessErrorCodeEnum businessErrorCode) {
        super(businessErrorCode.getMessage());
        this.businessErrorCode = businessErrorCode;
    }

    public BusinessException(String message) {
        super(message);
        this.businessErrorCode = null;
    }

    public BusinessException(BusinessErrorCodeEnum businessErrorCode, String message) {
        super(String.format("%s：%s", businessErrorCode.getMessage(), message));
        this.businessErrorCode = businessErrorCode;
    }

    public BusinessException(String message, RuntimeException cause) {
        super(message, cause);
        this.businessErrorCode = null;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.businessErrorCode = null;
    }

    public BusinessException(BusinessErrorCodeEnum businessErrorCode, Throwable cause) {
        super(businessErrorCode.getMessage(), cause);
        this.businessErrorCode = businessErrorCode;
    }

    public BusinessException(BusinessErrorCodeEnum businessErrorCode, String message, Throwable cause) {
        super(String.format("%s：%s", businessErrorCode.getMessage(), message), cause);
        this.businessErrorCode = businessErrorCode;
    }

    public BusinessErrorCodeEnum getBusinessErrorCode() {
        return businessErrorCode;
    }

    public int getHttpCode() {
        return businessErrorCode != null ? businessErrorCode.getHttpCode() : 500;
    }
}
