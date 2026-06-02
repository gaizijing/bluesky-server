package com.bluesky.controller;

import com.bluesky.common.Result;
import com.bluesky.dto.UserRequest;
import com.bluesky.service.UserService;
import com.bluesky.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 */
@Tag(name = "用户管理", description = "用户注册、修改密码、用户管理等接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody UserRequest request) {
        return Result.success(userService.register(request));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/change-password")
    public Result<Void> changePassword(@RequestHeader("Authorization") String token, @RequestParam String oldPassword, @RequestParam String newPassword) {
        String actualToken = token.replace("Bearer ", "");
        userService.changePassword(actualToken, oldPassword, newPassword);
        return Result.success();
    }

    @Operation(summary = "获取用户列表")
    @GetMapping("/list")
    public Result<List<UserVO>> getUserList() {
        return Result.success(userService.getUserList());
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable String id) {
        return Result.success(userService.getUserById(id));
    }

    @Operation(summary = "创建用户")
    @PostMapping
    public Result<UserVO> createUser(@Valid @RequestBody UserRequest request) {
        return Result.success(userService.createUser(request));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Result<UserVO> updateUser(@PathVariable String id, @Valid @RequestBody UserRequest request) {
        return Result.success(userService.updateUser(id, request));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @Operation(summary = "更新用户状态")
    @PutMapping("/{id}/status")
    public Result<UserVO> updateUserStatus(@PathVariable String id, @RequestParam String status) {
        return Result.success(userService.updateUserStatus(id, status));
    }
}
