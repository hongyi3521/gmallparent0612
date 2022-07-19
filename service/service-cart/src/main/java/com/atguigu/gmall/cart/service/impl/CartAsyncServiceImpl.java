package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.model.cart.CartInfo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CartAsyncServiceImpl implements CartAsyncService {
    @Autowired
    private CartInfoMapper cartInfoMapper;
    @Async
    @Override
    public void updateCartInfo(CartInfo cartInfo) {
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        // 两个条件锁定一个购物车数据
        queryWrapper.eq("user_id",cartInfo.getUserId());
        queryWrapper.eq("sku_id",cartInfo.getSkuId());
        cartInfoMapper.update(cartInfo,queryWrapper);
    }
    @Async
    @Override
    public void saveCartInto(CartInfo cartInfo) {
        cartInfoMapper.insert(cartInfo);
    }
    @Async
    @Override
    public void deleteCartInfo(String userId) {
        // 删除临时id数据
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        cartInfoMapper.delete(queryWrapper);
    }
    @Async
    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        queryWrapper.eq("sku_id",skuId);
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);
        cartInfoMapper.update(cartInfo,queryWrapper);
    }

    @Async
    @Override
    public void deleteCart(String userId, Long skuId) {
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        queryWrapper.eq("sku_id",skuId);
        cartInfoMapper.delete(queryWrapper);
    }
}
