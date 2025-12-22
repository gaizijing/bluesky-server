package com.lantian.lam.service;

import com.lantian.lam.model.entity.User;

public interface IUserService {

    User getByName(String name);
}
