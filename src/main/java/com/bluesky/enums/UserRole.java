package com.bluesky.enums;

import com.bluesky.exception.BusinessException;
import com.bluesky.common.ResultCode;

public enum UserRole {
    SUPER_ADMIN,
    REGION_ADMIN,
    REGION_OPERATOR,
    READ_ONLY;

    public static UserRole parse(String role) {
        if (role == null || role.isBlank()) {
            return READ_ONLY;
        }
        try {
            return UserRole.valueOf(role.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "未知角色: " + role);
        }
    }

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }
}
