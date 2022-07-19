package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;
    @Autowired
    private ListFeignClient listFeignClient;


    //    @Override
//    public Map<String, Object> getBySkuId(Long skuId) {
//        // 创建返回结果
//        Map<String,Object> result = new HashMap<>();
//        // 根据skuId获取sku信息，里面有基本信息和图片集合
//        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
//        result.put("skuInfo",skuInfo);
//        // 通过三级分类id查询分类信息
//        BaseCategoryView categoryView = productFeignClient.getCategoryViewByCategory3Id(skuInfo.getCategory3Id());
//        result.put("categoryView",categoryView);
//        // 获取sku价格
//        BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
//        result.put("price",skuPrice);
//        // 根据spuId，skuId 查询销售属性集合
//        List<SpuSaleAttr> spuSaleAttrList = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());
//        result.put("spuSaleAttrList",spuSaleAttrList);
//        // 根据spuId 查询销售属性联合对于skuId属性
//        Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
//        String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
//        result.put("valuesSkuJson",valuesSkuJson);
//        return result;
//    }
    @Override
    public Map<String, Object> getBySkuId(Long skuId) {
        // 利用juc实现多线程获取数据返回
        Map<String, Object> result = new HashMap<>();
        // 1、通过skuId查询skuInfo
        // supplyAsync有返回值
        CompletableFuture<SkuInfo> skuInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            result.put("skuInfo", skuInfo);
            return skuInfo;
        }, threadPoolExecutor);
        // 另开一个线程，获得skuInfoCompletableFuture线程的返回值
        // thenAccept方法：消费处理结果。接收任务的处理结果，并消费处理，无返回结果
        // 2、通过三级分类id查询分类信息
        CompletableFuture<Void> categoryViewFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo) -> {
            BaseCategoryView categoryView = productFeignClient.getCategoryViewByCategory3Id(skuInfo.getCategory3Id());
            result.put("categoryView", categoryView);
        }, threadPoolExecutor);
        // 3、根据spuId，skuId 查询销售属性集合
        CompletableFuture<Void> spuSaleAttrListFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo) -> {
            List<SpuSaleAttr> spuSaleAttrList = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());
            result.put("spuSaleAttrList", spuSaleAttrList);
        }, threadPoolExecutor);
        // 4、根据spuId 查询销售属性联合对于skuId属性
        CompletableFuture<Void> valuesSkuJsonFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo) -> {
            Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
            String valuesSkuJson = JSON.toJSONString(skuValueIdsMap);
            result.put("valuesSkuJson", valuesSkuJson);
        }, threadPoolExecutor);
        // 5、获取skuId对应商品价格
        CompletableFuture<Void> priceFuture = CompletableFuture.runAsync(() -> {
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            result.put("price", skuPrice);
        }, threadPoolExecutor);
        // 6、每次点击商品详情页，更新redis数据
        CompletableFuture<Void> skuIdFuture = CompletableFuture.runAsync(() -> {
            listFeignClient.incrHotScore(skuId);
        }, threadPoolExecutor);

        // 启动线程
        CompletableFuture.allOf(
                skuInfoCompletableFuture,
                categoryViewFuture,
                spuSaleAttrListFuture,
                valuesSkuJsonFuture,
                priceFuture,
                skuIdFuture
        ).join();
        return result;
    }
}
