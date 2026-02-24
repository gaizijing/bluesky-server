package com.bluesky.exception;

import com.bluesky.common.ResultCode;
import lombok.Getter;

/**
 * 自定义业务异常
 * 支持传入 ResultCode 枚举，保持错误码统一管理
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Integer code;

    // ========== 使用 ResultCode 构造（推荐） ==========

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    // ========== 直接传入 code + message ==========

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_SERVER_ERROR.getCode();
    }
}
