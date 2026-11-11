package com.carl.mall.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回体：{code, msg, data}
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok() {
        return build(ResultCode.SUCCESS, "success", null);
    }

    public static <T> Result<T> ok(T data) {
        return build(ResultCode.SUCCESS, "success", data);
    }

    public static <T> Result<T> ok(String msg, T data) {
        return build(ResultCode.SUCCESS, msg, data);
    }

    public static <T> Result<T> fail(String msg) {
        return build(ResultCode.SERVER_ERROR, msg, null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return build(code, msg, null);
    }

    private static <T> Result<T> build(int code, String msg, T data) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }
}
