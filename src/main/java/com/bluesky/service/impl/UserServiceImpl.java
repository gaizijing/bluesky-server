package com.bluesky.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluesky.dto.UserRequest;
import com.bluesky.entity.User;
import com.bluesky.exception.BusinessException;
import com.bluesky.mapper.UserMapper;
import com.bluesky.service.UserService;
import com.bluesky.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final com.bluesky.util.JwtUtil jwtUtil;

    @Override
    public UserVO register(UserRequest request) {
        return createUserInternal(request);
    }

    @Override
    public void changePassword(String token, String oldPassword, String newPassword) {
        String normalizedOldPassword = trimToNull(oldPassword);
        String normalizedNewPassword = trimToNull(newPassword);
        if (normalizedOldPassword == null) {
            throw new BusinessException(400, "旧密码不能为空");
        }
        if (normalizedNewPassword == null) {
            throw new BusinessException(400, "新密码不能为空");
        }
        if (normalizedNewPassword.length() < 6) {
            throw new BusinessException(400, "密码长度至少为6位");
        }

        // 从JWT中获取当前用户ID
        String currentUserId = jwtUtil.getUserIdFromToken(token);

        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(normalizedOldPassword, user.getPassword())) {
            throw new BusinessException(400, "旧密码不正确");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(normalizedNewPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public List<UserVO> getUserList() {
        List<User> users = userMapper.selectList(null);
        return users.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public UserVO getUserById(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return convertToVO(user);
    }

    @Override
    public UserVO createUser(UserRequest request) {
        return createUserInternal(request);
    }

    @Override
    public UserVO updateUser(String id, UserRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (isBlank(request.getUsername())) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (isBlank(request.getName())) {
            throw new BusinessException(400, "真实姓名不能为空");
        }
        if (isBlank(request.getEmail())) {
            throw new BusinessException(400, "邮箱不能为空");
        }
        if (isBlank(request.getPhone())) {
            throw new BusinessException(400, "手机号不能为空");
        }

        // 检查用户名是否已被其他用户使用
        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername().trim())
                        .ne(User::getId, id));
        if (existingUser != null) {
            throw new BusinessException(400, "用户名已存在");
        }

        // 更新用户信息
        user.setUsername(request.getUsername().trim());
        String password = trimToNull(request.getPassword());
        if (password != null) {
            if (password.length() < 6) {
                throw new BusinessException(400, "密码长度至少为6位");
            }
            user.setPassword(passwordEncoder.encode(password));
        }
        user.setName(request.getName().trim());
        user.setEmail(request.getEmail().trim());
        user.setPhone(request.getPhone().trim());
        user.setRole(normalizeRole(request.getRole()));
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.updateById(user);
        return convertToVO(user);
    }

    @Override
    public void deleteUser(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        userMapper.deleteById(id);
    }

    @Override
    public UserVO updateUserStatus(String id, String status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        user.setStatus(normalizeStatus(status));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return convertToVO(user);
    }

    private UserVO createUserInternal(UserRequest request) {
        String username = trimToNull(request.getUsername());
        String password = trimToNull(request.getPassword());
        String name = trimToNull(request.getName());
        String email = trimToNull(request.getEmail());
        String phone = trimToNull(request.getPhone());

        if (username == null) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (password == null) {
            throw new BusinessException(400, "密码不能为空");
        }
        if (name == null) {
            throw new BusinessException(400, "真实姓名不能为空");
        }
        if (email == null) {
            throw new BusinessException(400, "邮箱不能为空");
        }
        if (phone == null) {
            throw new BusinessException(400, "手机号不能为空");
        }

        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username));
        if (existingUser != null) {
            throw new BusinessException(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setName(name);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(normalizeRole(request.getRole()));
        user.setStatus("active");
        user.setLoginCount(0);
        user.setLastLoginTime(null);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);
        return convertToVO(user);
    }

    private String normalizeRole(String role) {
        String normalizedRole = trimToNull(role);
        if (normalizedRole == null) {
            return "user";
        }

        String lowerCaseRole = normalizedRole.toLowerCase();
        if (!"admin".equals(lowerCaseRole) && !"user".equals(lowerCaseRole)) {
            throw new BusinessException(400, "角色目前支持 admin 和 user");
        }
        return lowerCaseRole;
    }

    private String normalizeStatus(String status) {
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus == null) {
            throw new BusinessException(400, "状态不能为空");
        }

        String lowerCaseStatus = normalizedStatus.toLowerCase();
        if (!"active".equals(lowerCaseStatus) && !"inactive".equals(lowerCaseStatus) && !"locked".equals(lowerCaseStatus)) {
            throw new BusinessException(400, "无效的状态值");
        }
        return lowerCaseStatus;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 将User实体转换为UserVO
     */
    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setRole(user.getRole());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setLoginCount(user.getLoginCount());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }
}
