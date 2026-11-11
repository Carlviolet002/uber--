package com.carl.mall.common.result;

/**
 * 统一错误码
 */
public interface ResultCode {

    int SUCCESS = 200;
    int BAD_REQUEST = 400;
    int UNAUTHORIZED = 401;
    int FORBIDDEN = 403;
    int NOT_FOUND = 404;
    int SERVER_ERROR = 500;
}
