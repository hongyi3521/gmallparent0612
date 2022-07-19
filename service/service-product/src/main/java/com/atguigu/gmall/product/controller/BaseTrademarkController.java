package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
@Api(tags = "品牌管理")
@RestController
@RequestMapping("admin/product/baseTrademark")
public class BaseTrademarkController {
    @Autowired
    private BaseTrademarkService baseTrademarkService;

    // 获取品牌信息http://localhost/admin/product/baseTrademark/1/10
    @ApiOperation(value = "获取品牌信息")
    @GetMapping("{page}/{limit}")
    public Result index(@PathVariable Long page,
                                   @PathVariable Long limit) {
        Page<BaseTrademark> baseTrademarkPage = new Page<>(page, limit);
        IPage<BaseTrademark> iPage = baseTrademarkService.index(baseTrademarkPage);
        return Result.ok(iPage);
    }

    @ApiOperation(value ="添加品牌")
    @PostMapping("save")
    public Result save(@RequestBody BaseTrademark baseTrademark){
        baseTrademarkService.save(baseTrademark);
        return Result.ok();
    }

    @ApiOperation(value ="删除品牌")
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id){
        baseTrademarkService.removeById(id);
        return Result.ok();
    }


    @ApiOperation(value ="修改品牌")
    @PutMapping("update")
    public Result update(@RequestBody BaseTrademark baseTrademark) {
        baseTrademarkService.updateById(baseTrademark);
        return Result.ok();
    }

    @ApiOperation(value ="修改回显数据")
    @GetMapping("get/{id}")
    public Result get(@PathVariable Long id){
        BaseTrademark baseTrademark = baseTrademarkService.getById(id);
        return Result.ok(baseTrademark);
    }

    @ApiOperation(value = "查询所有品牌信息")
    @GetMapping("getTrademarkList")
    public Result getTrademarkList(){
        return Result.ok(baseTrademarkService.list(null));
    }
}
