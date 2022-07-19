package com.atguigu.gmall.user.service;

import com.atguigu.gmall.model.user.UserInfo;

public interface UserService {
    /**
     * 用户登录验证信息
     * @param userInfo
     * @return
     */
    UserInfo login(UserInfo userInfo);
}
