package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

public interface CartAsyncService {
    // 更新购物车
    void updateCartInfo(CartInfo cartInfo);
    // 添加购物车
    void saveCartInto(CartInfo cartInfo);
    /**
     * 删除
     * @param userId
     */
    void deleteCartInfo(String userId);

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

}
