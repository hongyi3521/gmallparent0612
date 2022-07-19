package com.atguigu.gmall.order.service.impl;

import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Long saveOrderInfo(OrderInfo orderInfo) {
        // orderInfo中有userId和orderDetailList
        // 计算总金额
        orderInfo.sumTotalAmount();
        // 设置订单状态 未支付
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        // 设置订单创建时间
        orderInfo.setCreateTime(new Date());
        // 设置订单交易编号（第三方支付用)
        // 时间戳+随机数
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        // 设置订单有效期限 expireTime
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);
        orderInfo.setExpireTime(calendar.getTime());
        // 设置进度状态
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());

        // 获取订单各个商品详细信息
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        // 准备订单描述 ,这里需要拼接所以使用可变长字符串
        StringBuffer tradeBody  = new StringBuffer();
        for (OrderDetail orderDetail : orderDetailList) {
            // 取出每个商品的描述拼接
            tradeBody.append(orderDetail.getSkuName() + " ");
        }
        // 判断描述信息是否超过100字体
        if(tradeBody.toString().length()>100){
            orderInfo.setTradeBody(tradeBody.toString().substring(0,100));
        }else{
            orderInfo.setTradeBody(tradeBody.toString());
        }
        // 保存结算订单到数据库
        orderInfoMapper.insert(orderInfo);
        // 保存每个商品订单的信息到数据库
        for (OrderDetail orderDetail : orderDetailList) {
            // 这些商品是一起支付的所以要统一订单编号
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insert(orderDetail);
        }
        return orderInfo.getId();
    }

    /**
     * 获取流水编号，第一次生成订单时，生成编号存入redis
     * @param userId
     * @return
     */
    @Override
    public String getTradeNo(String userId) {
        String tradeNokey = "user:"+userId+":tradeCode";
        // 定义键值
        String tradeNo = UUID.randomUUID().toString().replace("-","");
        // 存入redis
        redisTemplate.opsForValue().set(tradeNokey,tradeNo);
        return tradeNo;
    }

    /**
     * 用户如果利用浏览器跳回提交页的话，会得到一个流水编号，如果和redis中的一样，就返回false
     * @param userId 获取缓存中的流水号
     * @param tradeCodeNo   页面传递过来的流水号
     * @return
     */
    @Override
    public boolean checkTradeCode(String userId, String tradeCodeNo) {
        String tradeNokey = "user:"+userId+":tradeCode";
        String tradeNO = (String) redisTemplate.opsForValue().get(tradeNokey);
        return tradeCodeNo.equals(tradeNO);
    }

    /**
     * 用户提交之后，就把流水编号删除，提交就是生成了结算编号
     * @param userId
     */
    @Override
    public void deleteTradeNo(String userId) {
        String tradeNokey = "user:"+userId+":tradeCode";
        redisTemplate.delete(tradeNokey);
    }


    @Value("${ware.url}")
    private String WARE_URL;
    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        // 远程调用http://localhost:9001/hasStock?skuId=10221&num=2
        String result = HttpClientUtil.doGet(WARE_URL + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        return "1".equals(result);
    }
}
