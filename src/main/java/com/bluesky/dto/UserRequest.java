package com.bluesky.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户请求DTO
 */
@Data
public class UserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度应在3-50之间")
    private String username;

    @Size(min = 6, message = "密码长度至少为6位")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    private String name;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^[0-9\\-+()\\s]{6,20}$", message = "手机号格式不正确")
    private String phone;

    @Pattern(regexp = "(?i)^(admin|user)$", message = "角色目前支持 admin 和 user")
    private String role;
}
