package com.lantian.lam.controller;

import com.lantian.lam.annotation.ResponseWrapper;
import com.lantian.lam.model.entity.User;
import com.lantian.lam.service.IUserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("lam")
public class UserController {

    @Resource
    private IUserService userService;

    @GetMapping("get/{name}")
    @ResponseWrapper
    public User getByName(@PathVariable String name) {
        return userService.getByName(name);
    }
}
