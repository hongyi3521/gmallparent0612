package com.atguigu.gmall.list.service;

import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;

import java.io.IOException;

public interface SearchService {
    /**
     * 上架商品列表
     * @param skuId
     */
    void upperGoods(Long skuId);

    /**
     * 下架商品列表
     * @param skuId
     */
    void lowerGoods(Long skuId);

    /**
     * 用户点击商品详情，更新商品热度
     * @param skuId
     */
    void incrHotScore(Long skuId);

    /**
     * 条件检索商品
     * @param searchParam
     * @return
     */
    SearchResponseVo search(SearchParam searchParam);
}
