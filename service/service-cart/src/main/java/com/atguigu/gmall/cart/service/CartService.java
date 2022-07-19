package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface CartService extends IService<CartInfo> {
    // 添加购物车 用户Id，商品Id，商品数量。
    void addToCart(Long skuId, String userId, Integer skuNum);

    /**
     * 通过用户的id得到购物车信息显示
     * @param userId
     * @param userTempId
     * @return
     */
    List<CartInfo> getCartList(String userId,String userTempId);

    /**
     * 购物车选中状态变更
     * @param userId
     * @param isChecked
     * @param skuId
     */
    void checkCart(String userId ,Integer isChecked,Long skuId);

    /**
     * 删除购物车
     * @param userId
     * @param skuId
     */
    void deleteCart(String userId, Long skuId);

    /**
     * 获取用户选中的购物车商品集合
     * @param userId
     * @return
     */
    List<CartInfo> getCartCheckedList(String userId);

    /**
     * 根据用户Id查询购物车最新数据并放入缓存
     * @param userId
     * @return
     */
    List<CartInfo> loadCartCache(String userId);
}
