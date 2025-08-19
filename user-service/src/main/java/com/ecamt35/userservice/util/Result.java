package com.ecamt35.userservice.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private Integer code;
    private String errorMsg;
    private Object data;
    private Long total;

    public static Result success() {
        return new Result(true, AppHttpCodeEnum.SUCCESS.getCode(),
                AppHttpCodeEnum.SUCCESS.errorMsg, null, null);
    }

    public static Result success(Object data) {
        return new Result(true, AppHttpCodeEnum.SUCCESS.getCode(),
                AppHttpCodeEnum.SUCCESS.errorMsg, data, null);
    }

    public static Result success(List<?> data) {

        return new Result(true, AppHttpCodeEnum.SUCCESS.getCode(),
                AppHttpCodeEnum.SUCCESS.errorMsg, data, (long) data.size());
    }

    public static Result success(List<?> data, Long selectTotal) {
        ListResult listResult = new ListResult();
        listResult.setList(data);
        listResult.setSelectTotal(selectTotal);
        return new Result(true, AppHttpCodeEnum.SUCCESS.getCode(),
                AppHttpCodeEnum.SUCCESS.errorMsg, listResult, (long) data.size());
    }

    public static Result fail(String errorMsg) {
        return new Result(false, AppHttpCodeEnum.SYSTEM_ERROR.getCode(),
                errorMsg, null, null);
    }

    public static Result fail(AppHttpCodeEnum httpCodeEnum) {
        return new Result(false, httpCodeEnum.getCode(),
                httpCodeEnum.getErrorMsg(), null, null);
    }

    public static Result fail(int code, String errorMsg) {
        return new Result(false, code,
                errorMsg, null, null);
    }
}
