package com.bluesky.service;

import com.bluesky.dto.UserRequest;
import com.bluesky.vo.UserVO;

import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     */
    UserVO register(UserRequest request);

    /**
     * 修改密码
     */
    void changePassword(String token, String oldPassword, String newPassword);

    /**
     * 获取用户列表
     */
    List<UserVO> getUserList();

    /**
     * 根据ID获取用户详情
     */
    UserVO getUserById(String id);

    /**
     * 创建用户
     */
    UserVO createUser(UserRequest request);

    /**
     * 更新用户
     */
    UserVO updateUser(String id, UserRequest request);

    /**
     * 删除用户
     */
    void deleteUser(String id);

    /**
     * 更新用户状态
     */
    UserVO updateUserStatus(String id, String status);
}
