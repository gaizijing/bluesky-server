package com.bluesky.exception;

import com.bluesky.common.Result;
import com.bluesky.common.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局统一异常处理器
 * <p>
 * 所有异常统一在此捕获并转换为标准 Result 响应，
 * HTTP 状态码始终为 200，业务状态码通过 Result.code 区分。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 业务异常 ====================

    /**
     * 自定义业务异常（主动抛出）
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("[业务异常] code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    // ==================== 参数校验异常 ====================

    /**
     * @Valid 注解校验失败（@RequestBody 参数）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[参数校验失败] {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * @Validated 注解校验失败（@RequestParam / @PathVariable）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[参数约束违反] {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 表单参数绑定失败（@ModelAttribute 等）
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[表单参数绑定失败] {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 必填请求参数缺失
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        String message = "缺少必填参数: " + e.getParameterName();
        log.warn("[缺少参数] {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 请求体无法解析（JSON 格式错误等）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.warn("[请求体解析失败] {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST, "请求体格式错误，请检查 JSON 格式");
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = "参数 '" + e.getName() + "' 类型错误，期望类型: "
                + (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "未知");
        log.warn("[参数类型不匹配] {}", message);
        return Result.error(ResultCode.BAD_REQUEST, message);
    }

    // ==================== 认证 / 授权异常 ====================

    /**
     * 用户名或密码错误
     */
    @ExceptionHandler({ BadCredentialsException.class, UsernameNotFoundException.class })
    public Result<?> handleAuthenticationException(Exception e) {
        log.warn("[认证失败] {}", e.getMessage());
        return Result.error(ResultCode.USER_PASSWORD_ERROR);
    }

    /**
     * 权限不足（403）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("[权限不足] {}", e.getMessage());
        return Result.error(ResultCode.FORBIDDEN);
    }

    // ==================== HTTP 协议异常 ====================

    /**
     * 接口不存在（404）
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<?> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.warn("[接口不存在] {} {}", e.getHttpMethod(), e.getRequestURL());
        return Result.error(ResultCode.NOT_FOUND, "接口不存在: " + e.getRequestURL());
    }

    /**
     * 请求方法不支持（405）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("[方法不支持] {}", e.getMessage());
        return Result.error(ResultCode.METHOD_NOT_ALLOWED, "不支持 " + e.getMethod() + " 请求方式");
    }

    // ==================== 兜底异常 ====================

    /**
     * 其他未捕获异常（500）
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("[系统异常] ", e);
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR);
    }
}
