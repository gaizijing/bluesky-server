package com.lantian.lam.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Response<T> {

    private int code;   // 状态码
    private String message; // 响应信息
    private T data;     // 响应数据

    public static <T> Response<T> success(T data) {
        return new Response<>(200, "操作成功", data);
    }

    public static <T> Response<T> error(String message) {
        return new Response<>(500, message, null);
    }

    public static <T> Response<T> error(int code, String message) {
        return new Response<>(code, message, null);
    }
}
