package com.bluesky.security;

import com.bluesky.exception.BusinessException;
import com.bluesky.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Slf4j
@Component
public class RegionPermissionInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 仅对「显式声明 @RequestParam regionId」的接口要求 query 参数。
     * 按资源 ID 操作的接口在 Service 内通过实体 regionId 做 assertRegionAccess。
     */
    private static final List<String> REGION_ID_QUERY_REQUIRED = List.of(
            "GET:/warnings",
            "GET:/flyability/**",
            "GET:/risk/heatmap",
            "GET:/no-fly-zones",
            "GET:/sim/sessions",
            "POST:/sim/sessions",
            "POST:/no-fly-zones/import",
            "GET:/landing-points",
            "GET:/routes",
            "DELETE:/routes",
            "POST:/routes",
            "POST:/routes/import"
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
        String key = method.toUpperCase() + ":" + path;
        return REGION_ID_QUERY_REQUIRED.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, key));
    }
}
