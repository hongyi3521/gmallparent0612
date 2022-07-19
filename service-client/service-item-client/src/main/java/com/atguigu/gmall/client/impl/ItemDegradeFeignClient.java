package com.atguigu.gmall.client.impl;

import com.atguigu.gmall.client.ItemFeignClient;
import com.atguigu.gmall.common.result.Result;
import org.springframework.stereotype.Component;

@Component
public class ItemDegradeFeignClient implements ItemFeignClient {
    @Override
    public Result getItem(Long skuId) {
        return Result.fail();
    }
}
