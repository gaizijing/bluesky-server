package com.bluesky.security;

import com.bluesky.enums.UserRole;
import lombok.Data;

import java.util.List;

@Data
public class LoginUser {
    private String userId;
    private String username;
    private UserRole role;
    private List<String> regionIds;
}
