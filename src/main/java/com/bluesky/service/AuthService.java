package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.dto.LoginRequest;
import com.bluesky.entity.User;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.UserMapper;
import com.bluesky.util.JwtUtil;
import com.bluesky.vo.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 认证服务
 *
 * @author BlueSky Team
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername()));

        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        // 检查用户状态
        if (!"active".equals(user.getStatus())) {
            throw new BusinessException(403, "用户已被禁用");
        }

        // 更新登录信息
        user.setLastLoginTime(LocalDateTime.now());
        user.setLoginCount(user.getLoginCount() + 1);
        userMapper.updateById(user);

        // 生成Token
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        // 构建响应
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getName(),
                "admin", // 这里简化处理,实际应从角色表查询
                Arrays.asList("dashboard", "setting", "map") // 这里简化处理,实际应从权限表查询
        );

        return new LoginResponse(token, userInfo);
    }

    /**
     * 获取用户信息
     */
    public LoginResponse.UserInfo getUserInfo(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        return new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getName(),
                "admin",
                Arrays.asList("dashboard", "setting", "map"));
    }
}
