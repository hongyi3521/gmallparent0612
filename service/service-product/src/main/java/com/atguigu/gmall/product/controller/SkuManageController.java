package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuImage;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("admin/product/")
public class SkuManageController {
    //http://api.gmall.com/admin/product/spuImageList/{spuId}

    @Autowired
    private ManageService manageService;

    // 查出对应商品的spu图片
    @GetMapping("spuImageList/{spuId}")
    public Result getSpuImageList(@PathVariable Long spuId) {
        List<SpuImage> spuImageList = manageService.getSpuImageList(spuId);
        return Result.ok(spuImageList);
    }

    // http://api.gmall.com/admin/product/spuSaleAttrList/{spuId}
    // 根据spuid获取销售属性值集合
    @GetMapping("spuSaleAttrList/{spuId}")
    public Result getSpuSaleAttrList(@PathVariable Long spuId) {
        // spuSaleAttrList中有spuSaleAttrValue集合
        List<SpuSaleAttr> spuSaleAttrList = manageService.getSpuSaleAttrList(spuId);
        return Result.ok(spuSaleAttrList);
    }

    // http://api.gmall.com/admin/product/saveSkuInfo
    @PostMapping("saveSkuInfo")
    public Result saveSkuInfo(@RequestBody SkuInfo skuInfo) {
        manageService.saveSkuInfo(skuInfo);
        return Result.ok();
    }

    //    http://api.gmall.com/admin/product/list/{page}/{limit}
    @GetMapping("list/{page}/{limit}")
    public Result index(@PathVariable("page") Long page,
                        @PathVariable("limit") Long limit) {
        Page<SkuInfo> skuInfoPage = new Page<>(page, limit);
        IPage<SkuInfo> iPage = manageService.index(skuInfoPage);
        return Result.ok(iPage);
    }
    // 上架商品 http://api.gmall.com/admin/product/onSale/{skuId}
    @GetMapping("onSale/{skuId}")
    public Result onSale(@PathVariable Long skuId){
        manageService.onSale(skuId);
        return Result.ok();
    }


    // 下架商品 http://api.gmall.com/admin/product/cancelSale/{skuId}
    @GetMapping("cancelSale/{skuId}")
    public Result cancelSale(@PathVariable Long skuId){
        manageService.cancelSale(skuId);
        return Result.ok();
    }
}

