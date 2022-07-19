package com.atguigu.gmall.user.service;

import com.atguigu.gmall.model.user.UserAddress;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface UserAddressService extends IService<UserAddress> {
    // 根据用户id获取用户地址列表
    List<UserAddress> findUserAddressByUserId(String userId);

}
