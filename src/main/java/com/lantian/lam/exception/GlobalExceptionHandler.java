package com.lantian.lam.exception;

import com.lantian.lam.model.response.Response;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Response<String> handleException(Exception e) {
        // 打印异常日志
        e.printStackTrace();
        return Response.error("系统异常，请稍后再试");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<String> handleBadRequest(IllegalArgumentException e) {
        // 打印异常日志
        e.printStackTrace();
        return Response.error("请求参数不合法");
    }

    @ExceptionHandler(CustomException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Response<String> handleCustomException(CustomException e) {
        // 打印异常日志
        e.printStackTrace();
        return Response.error(e.getCode(), e.getMessage());
    }
}
