package com.atguigu.gmall.order.controller;

import com.alibaba.nacos.client.naming.utils.StringUtils;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("api/order")
public class OrderApiController {

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private OrderService orderService;

    /**
     * 用户点击去结算，生成订单
     *
     * @param request
     * @return
     */
    @GetMapping("auth/trade")
    public Result<Map<String, Object>> trade(HttpServletRequest request) {

        // 1、获取用户id，用户登录了，网关会发送数据过来
        String userId = AuthContextHolder.getUserId(request);
        // 2、一个用户会有多个收货地址
        List<UserAddress> addressList = userFeignClient.findUserAddressByUserId(userId);
        // 3、得到用户选中的商品集合
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);
        // 4、封装数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        // 遍历商品信息
        for (CartInfo cartInfo : cartCheckedList) {
            // 取出实时价格
            BigDecimal skuPrice = cartInfo.getSkuPrice();
            // 这个商品的购买商品
            Integer skuNum = cartInfo.getSkuNum();
            // 获取商品名称
            String skuName = cartInfo.getSkuName();
            // 商品图片
            String imgUrl = cartInfo.getImgUrl();
            // 商品的skuid
            Long skuId = cartInfo.getSkuId();
            // 每个商品都有一个小订单，结算的订单相加，得到结算订单
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setOrderPrice(skuPrice);
            orderDetail.setImgUrl(imgUrl);
            orderDetail.setSkuName(skuName);
            orderDetail.setCreateTime(new Date());
            orderDetail.setSkuId(skuId);
            orderDetail.setSkuNum(skuNum);
            orderDetailList.add(orderDetail);
        }

        // 5、计算结算订单的总共价格
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailList);
        orderInfo.sumTotalAmount();

        // Result的data是泛型数据，我们这里设置t为map
        Map<String, Object> result = new HashMap<>();
        // 地址集合
        result.put("userAddressList", addressList);
        // 订单集合
        result.put("detailArrayList", orderDetailList);
        // 购买商品类别总数量
        result.put("totalNum", orderDetailList.size());
        // 保存总共应支付金额
        result.put("totalAmount", orderInfo.getTotalAmount());

        // 生成流水编号，前端提交订单的时候会带着这个编号，如果已经提交guol
        String tradeNo = orderService.getTradeNo(userId);
        result.put("tradeNo", tradeNo);
        return Result.ok(result);
    }



    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    /**
     * web-all得到总订单数据后,跳转页面，那个页面直接调用保存得到订单号显示
     *
     * @param orderInfo
     * @param request
     * @return
     */
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request) {
        // 获取用户id
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));
        // 获取前台页面的流水号
        String tradeNo = request.getParameter("tradeNo");
        // 用户第一次提交订单肯定没问题，用户提交订单保存数据生成订单号的时候，你再回退点击提交订单，就会有问题，
        // 因为这个时候还没有完成第一次提交订单，结算流水号还没删除
        // 调用服务层的比较方法
        boolean flag = orderService.checkTradeCode(userId, tradeNo);
        if (!flag) {
            // 比较失败！
            return Result.fail().message("不能重复提交订单！");
        }
        //  删除流水号
        orderService.deleteTradeNo(userId);

        // 线程集合
        List<String> errorList = new ArrayList<>();
        List<CompletableFuture> futureList = new ArrayList<>();
        // 验证库存：
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            CompletableFuture<Void> checkStockCompletableFuture = CompletableFuture.runAsync(() -> {
                // 验证库存：
                boolean result = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                if (!result) {
                    errorList.add(orderDetail.getSkuName() + "库存不足！");
                }
            }, threadPoolExecutor);
            futureList.add(checkStockCompletableFuture);

            CompletableFuture<Void> checkPriceCompletableFuture = CompletableFuture.runAsync(() -> {
                // 验证价格：
                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                if (orderDetail.getOrderPrice().compareTo(skuPrice) != 0) {
                    // 重新查询价格！
                    cartFeignClient.loadCartCache(userId);
                    errorList.add(orderDetail.getSkuName() + "价格有变动！");
                }
            }, threadPoolExecutor);
            futureList.add(checkPriceCompletableFuture);
        }

        // 合并线程,将线程遇到到问题合并返回，将集合转成数组，CompletableFuture类型的数组
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()])).join();
        if(errorList.size() > 0) {
            return Result.fail().message(StringUtils.join(errorList, ","));
        }
        // 保存订单,返回一个订单号
        Long orderId = orderService.saveOrderInfo(orderInfo);

        return Result.ok(orderId);
    }

}
