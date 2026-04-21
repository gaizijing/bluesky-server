package com.bluesky.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应VO
 */
@Data
public class UserVO {

    private String id;
    private String username;
    private String name;
    private String email;
    private String phone;
    private String status;
    private String role;
    private LocalDateTime lastLoginTime;
    private Integer loginCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
