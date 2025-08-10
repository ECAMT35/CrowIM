package com.ecamt35.messageservice.util;

public class BusinessException extends RuntimeException {

    private BusinessErrorCodeEnum businessErrorCode;

    public BusinessException(BusinessErrorCodeEnum businessErrorCode) {
        super(businessErrorCode.getMessage());
        this.businessErrorCode = businessErrorCode;
    }

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(BusinessErrorCodeEnum businessErrorCode, String message) {
        super(businessErrorCode.getMessage() + message);
        this.businessErrorCode = businessErrorCode;
    }

    public int getHttpCode() {
        return businessErrorCode.getHttpCode();
    }
}
