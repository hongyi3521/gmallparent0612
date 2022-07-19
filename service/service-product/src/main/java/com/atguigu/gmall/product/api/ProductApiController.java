package com.atguigu.gmall.product.api;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/product")
public class ProductApiController {

    @Autowired
    private ManageService manageService;

    @ApiOperation("根据skuId获取sku信息")
    @GetMapping("inner/getSkuInfo/{skuId}")
    public SkuInfo getSkuInfo(@PathVariable Long skuId) {
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);
        return skuInfo;
    }

    @ApiOperation("通过三级分类id查询分类信息")
    @GetMapping("inner/getCategoryView/{category3Id}")
    public BaseCategoryView getCategoryViewByCategory3Id(@PathVariable Long category3Id) {
        return manageService.getCategoryViewByCategory3Id(category3Id);
    }

    @ApiOperation("获取sku价格")
    @GetMapping("inner/getSkuPrice/{skuId}")
    public BigDecimal getSkuPrice(@PathVariable Long skuId) {
        return manageService.getSkuPrice(skuId);
    }

    @ApiOperation("根据spuId，skuId 查询销售属性集合")
    @GetMapping("inner/getSpuSaleAttrListCheckBySku/{skuId}/{spuId}")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(@PathVariable Long skuId,
                                                          @PathVariable Long spuId) {
        return manageService.getSpuSaleAttrListCheckBySku(skuId, spuId);
    }

    /**
     * 根据spuId 查询map 集合属性
     * @param spuId
     * @return
     */
    @ApiOperation("根据spuId 查询map 集合属性")
    @GetMapping("inner/getSkuValueIdsMap/{spuId}")
    public Map getSkuValueIdsMap(@PathVariable("spuId") Long spuId) {
        return manageService.getSkuValueIdsMap(spuId);
    }

    @ApiOperation("获取全部分类信息")
    @GetMapping("getBaseCategoryList")
    public Result getBaseCategoryList() {
        List<JSONObject> baseCategoryList = manageService.getBaseCategoryList();
        return Result.ok(baseCategoryList);
    }

    @ApiOperation("通过品牌Id来查询数据")
    @GetMapping("inner/getTrademark/{tmId}")
    public BaseTrademark getTrademark(@PathVariable("tmId")Long tmId){
        BaseTrademark baseTrademark = manageService.getTrademark(tmId);
        return baseTrademark;
    }

    @ApiOperation("通过skuId 来查询数据")
    @GetMapping("inner/getAttrList/{skuId}")
    public List<BaseAttrInfo> getAttrList(@PathVariable("skuId") Long skuId){
        List<BaseAttrInfo> attrInfoList = manageService.getAttrList(skuId);
        return attrInfoList;
    }

}
