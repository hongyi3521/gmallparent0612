package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.atguigu.gmall.model.product.BaseAttrValue;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.extension.api.R;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "商品基础属性接口")
@RestController
@RequestMapping("admin/product")
//@CrossOrigin
public class BaseManageController {
    @Autowired
    private ManageService manageService;

    /**
     * 查询所有一级分类
     *
     * @return
     */
    @GetMapping("getCategory1")
    public Result getCategory1() {
        return Result.ok(manageService.getCategory1());
    }

    /**
     * // 查询所有二级分类
     *
     * @param category1Id
     * @return
     */
    @GetMapping("getCategory2/{category1Id}")
    public Result getCategory2(@PathVariable Long category1Id) {
        return Result.ok(manageService.getCategory2(category1Id));
    }

    /**
     * 查询所有三级分类
     *
     * @param category2Id
     * @return
     */
    @GetMapping("getCategory3/{category2Id}")
    public Result getCategory3(@PathVariable Long category2Id) {
        return Result.ok(manageService.getCategory3(category2Id));
    }

    /**
     * 根据分类id获取平台属性数据
     * @param category1Id
     * @param category2Id
     * @param category3Id
     * @return
     */
    @GetMapping("attrInfoList/{category1Id}/{category2Id}/{category3Id}")
    public Result getAttrInfoList(@PathVariable Long category1Id,
                                  @PathVariable Long category2Id,
                                  @PathVariable Long category3Id) {
        List<BaseAttrInfo> attrInfoList = manageService.getAttrInfoList(category1Id, category2Id, category3Id);
        return Result.ok(attrInfoList);
    }

    /**
     * 修改数据，回显平台数据值
     * @param attrId
     * @return
     */
    @GetMapping("getAttrValueList/{attrId}")
    public Result getAttrValueList(@PathVariable Long attrId){
        BaseAttrInfo attrInfo = manageService.getAttrInfo(attrId);
        List<BaseAttrValue> attrValueList = attrInfo.getAttrValueList();
        return Result.ok(attrValueList);
    }

    /**
     * 保存或修改平台属性数据
     * @param baseAttrInfo
     * @return
     */
    @PostMapping("saveAttrInfo")
    public Result saveAttrInfo(@RequestBody BaseAttrInfo baseAttrInfo){
        // 前台数据都被封装到该对象中baseAttrInfo
        manageService.saveAttrInfo(baseAttrInfo);
        return Result.ok();
    }
}
