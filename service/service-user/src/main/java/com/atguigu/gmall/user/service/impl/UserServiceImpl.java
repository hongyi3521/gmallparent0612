package com.atguigu.gmall.user.service.impl;

import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.mapper.UserInfoMapper;
import com.atguigu.gmall.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserInfoMapper userInfoMapper;
    @Override
    public UserInfo login(UserInfo userInfo) {
        // 出于安全考虑，对密码进行加密后存入数据库，查询的时候，密码也要加密后查询
        String paddwd = DigestUtils.md5DigestAsHex(userInfo.getPasswd().getBytes());
        // 构建查询器
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("login_name",userInfo.getLoginName());
        queryWrapper.eq("passwd",paddwd);
        UserInfo info = userInfoMapper.selectOne(queryWrapper);
        return info;
    }
}
