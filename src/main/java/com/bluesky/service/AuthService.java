package com.bluesky.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.dto.LoginRequest;
import com.bluesky.entity.User;
import com.bluesky.enums.UserRole;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.UserMapper;
import com.bluesky.util.JwtUtil;
import com.bluesky.vo.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserRegionService userRegionService;

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!"active".equals(user.getStatus())) {
            throw new BusinessException(403, "用户已被禁用");
        }

        user.setLastLoginTime(LocalDateTime.now());
        user.setLoginCount(user.getLoginCount() == null ? 1 : user.getLoginCount() + 1);
        userMapper.updateById(user);

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        return new LoginResponse(token, buildUserInfo(user));
    }

    public LoginResponse.UserInfo getUserInfo(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return buildUserInfo(user);
    }

    private LoginResponse.UserInfo buildUserInfo(User user) {
        UserRole role = UserRole.parse(user.getRole());
        List<String> regionIds = userRegionService.listRegionIdsByUserId(user.getId(), role);
        return new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getName(),
                List.of(role.name()),
                regionIds,
                Arrays.asList("dashboard", "setting", "map"));
    }
}
