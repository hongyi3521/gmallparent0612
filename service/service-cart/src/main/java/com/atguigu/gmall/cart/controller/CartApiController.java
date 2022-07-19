package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("api/cart")
public class CartApiController {

    @Autowired
    private CartService cartService;


    @PostMapping("addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable("skuId") Long skuId,
                            @PathVariable("skuNum") Integer skuNum,
                            HttpServletRequest request) {
        // 用户登录之后，userId会通过网关发送到服务端
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)) {
            userId = AuthContextHolder.getUserTempId(request);
        }
        cartService.addToCart(skuId, userId, skuNum);
        return Result.ok();
    }

    @GetMapping("cartList")
    public Result cartList(HttpServletRequest request) {
        String userId = AuthContextHolder.getUserId(request);
        String userTempId = AuthContextHolder.getUserTempId(request);
        List<CartInfo> cartList = cartService.getCartList(userId, userTempId);
        return Result.ok(cartList);
    }

    // 更新选中状态
    @GetMapping("checkCart/{skuId}/{isChecked}")
    public Result checkCart(@PathVariable("skuId") Long skuId,
                            @PathVariable("isChecked") Integer isChecked,
                            HttpServletRequest request) {
        // 获取用户id
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)) {
            // 未登录就获取临时用户id
            userId = AuthContextHolder.getUserTempId(request);
        }
        cartService.checkCart(userId, isChecked, skuId);
        return Result.ok();
    }

    @DeleteMapping("deleteCart/{skuId}")
    public Result deleteCart(@PathVariable("skuId") Long skuId, HttpServletRequest request) {
        String userId = AuthContextHolder.getUserId(request);
        if (StringUtils.isEmpty(userId)) {
            // 未登录就获取临时用户id
            userId = AuthContextHolder.getUserTempId(request);
        }
        cartService.deleteCart(userId, skuId);
        return Result.ok();
    }

    @GetMapping("getCartCheckedList/{userId}")
    public List<CartInfo> getCartCheckedList(@PathVariable("userId") String userId) {
        List<CartInfo> cartList = cartService.getCartCheckedList(userId);
        return cartList;
    }


    // 加载数据
    @GetMapping("loadCartCache/{userId}")
    public Result loadCartCache(@PathVariable("userId") String userId){
        cartService.loadCartCache(userId);
        return Result.ok();
    }
}
