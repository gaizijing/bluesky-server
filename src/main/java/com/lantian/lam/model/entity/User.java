package com.lantian.lam.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("public.user")
public class User {
    private Integer id;

    private String username;

    private String password;

}
