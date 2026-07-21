package com.hz.crm.common.api;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResult<T> {

    private boolean success;

    private String code;

    private String message;

    private T data;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> result = new ApiResult<T>();
        result.setSuccess(true);
        result.setCode("0");
        result.setMessage("处理成功");
        result.setData(data);
        return result;
    }

    public static <T> ApiResult<T> fail(String code, String message) {
        ApiResult<T> result = new ApiResult<T>();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
