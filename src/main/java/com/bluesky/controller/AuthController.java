package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.dto.LoginRequest;
import com.bluesky.service.AuthService;
import com.bluesky.util.JwtUtil;
import com.bluesky.vo.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@Tag(name = "认证授权", description = "用户登录、登出、获取用户信息等接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Map<String, Boolean>> logout() {
        Map<String, Boolean> result = new HashMap<>();
        result.put("success", true);
        return Result.success(result);
    }

    @Operation(summary = "获取用户信息")
    @GetMapping("/userInfo")
    public Result<LoginResponse.UserInfo> getUserInfo(@RequestHeader("Authorization") String token) {
        String actualToken = token.replace("Bearer ", "");
        String userId = jwtUtil.getUserIdFromToken(actualToken);
        return Result.success(authService.getUserInfo(userId));
    }
}
