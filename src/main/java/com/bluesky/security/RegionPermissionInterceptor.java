package com.bluesky.security;

import com.bluesky.exception.BusinessException;
import com.bluesky.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Slf4j
@Component
public class RegionPermissionInterceptor implements HandlerInterceptor {

    /** 尚未细化的 V2 接口：仍按前缀要求 regionId */
    private static final Set<String> REGION_REQUIRED_PREFIXES = Set.of(
            "/warnings",
            "/flyability",
            "/risk",
            "/no-fly-zones",
            "/sim/sessions"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getServletPath();
        String method = request.getMethod();

        if (!requiresRegionIdParam(method, path)) {
            return true;
        }

        String regionId = request.getParameter("regionId");
        if (regionId == null || regionId.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "缺少必填参数 regionId");
        }

        LoginUser user = SecurityUtils.currentUser();
        if (user == null) {
            return true;
        }
        if (user.getRole().isSuperAdmin()) {
            return true;
        }
        if (user.getRegionIds() == null || !user.getRegionIds().contains(regionId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问 Region: " + regionId);
        }
        return true;
    }

    private boolean requiresRegionIdParam(String method, String path) {
        if ("GET".equalsIgnoreCase(method) && "/landing-points".equals(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/routes".equals(path)) {
            return true;
        }
        if ("DELETE".equalsIgnoreCase(method) && "/routes".equals(path)) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && ("/routes".equals(path) || "/routes/import".equals(path))) {
            return true;
        }
        return REGION_REQUIRED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
