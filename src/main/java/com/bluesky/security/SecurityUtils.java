package com.bluesky.security;

import com.bluesky.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return null;
        }
        return loginUser;
    }

    public static LoginUser requireUser() {
        LoginUser user = currentUser();
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("未登录");
        }
        return user;
    }

    public static boolean isSuperAdmin() {
        LoginUser user = currentUser();
        return user != null && user.getRole() == UserRole.SUPER_ADMIN;
    }
}
