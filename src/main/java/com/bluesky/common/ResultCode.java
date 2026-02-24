package com.bluesky.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举
 * 规则：2xx 成功，4xx 客户端错误，5xx 服务端错误
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ========== 成功 ==========
    SUCCESS(200, "操作成功"),

    // ========== 客户端错误 4xx ==========
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    // ========== 服务端错误 5xx ==========
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂不可用"),

    // ========== 业务错误 (以 1xxx 开头) ==========
    // 用户相关
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "用户名或密码错误"),
    USER_ACCOUNT_DISABLED(1003, "账号已被禁用"),
    USER_ACCOUNT_LOCKED(1004, "账号已被锁定"),

    // 重点关注区域相关
    POINT_NOT_FOUND(2001, "重点关注区域不存在"),
    POINT_ALREADY_EXISTS(2002, "重点关注区域已存在"),

    // 飞行任务相关
    TASK_NOT_FOUND(3001, "飞行任务不存在"),
    TASK_STATUS_INVALID(3002, "任务状态流转不合法"),

    // 气象数据相关
    WEATHER_DATA_NOT_FOUND(4001, "气象数据暂无"),
    WEATHER_DATA_EXPIRED(4002, "气象数据已过期");

    /** HTTP / 业务状态码 */
    private final Integer code;

    /** 描述信息 */
    private final String message;
}
