package com.atguigu.gmall.client;

import com.atguigu.gmall.client.impl.ItemDegradeFeignClient;
import com.atguigu.gmall.common.result.Result;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "service-item",fallback = ItemDegradeFeignClient.class)
public interface ItemFeignClient {

    // 获取sku详情信息")
    @GetMapping("api/item/{skuId}")
    Result getItem(@PathVariable("skuId") Long skuId);
}
