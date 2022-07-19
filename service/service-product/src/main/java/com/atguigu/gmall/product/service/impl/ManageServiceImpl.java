package com.atguigu.gmall.product.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.Condition;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ManageServiceImpl extends ServiceImpl<SpuInfoMapper, SpuInfo> implements ManageService {

    @Resource
    private BaseAttrInfoMapper baseAttrInfoMapper;
    @Resource
    private BaseAttrValueMapper baseAttrValueMapper;
    @Resource
    private BaseCategory1Mapper baseCategory1Mapper;
    @Resource
    private BaseCategory2Mapper baseCategory2Mapper;
    @Resource
    private BaseCategory3Mapper baseCategory3Mapper;
    @Resource
    private BaseSaleAttrMapper baseSaleAttrMapper;
    @Resource
    private SpuInfoMapper spuInfoMapper;
    @Resource
    private SpuImageMapper spuImageMapper;
    @Resource
    private SpuSaleAttrMapper spuSaleAttrMapper;
    @Resource
    private SpuSaleAttrValueMapper spuSaleAttrValueMapper;
    @Resource
    private SkuInfoMapper skuInfoMapper;
    @Resource
    private SkuImageMapper skuImageMapper;
    @Resource
    private SkuAttrValueMapper skuAttrValueMapper;

    @Resource
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;
    @Resource
    private BaseCategoryViewMapper baseCategoryViewMapper;

    @Resource
    private BaseTrademarkMapper baseTrademarkMapper;

    @Override
    public List<BaseCategory1> getCategory1() {
        return baseCategory1Mapper.selectList(null);
    }

    @Override
    public List<BaseCategory2> getCategory2(Long category1Id) {
        return baseCategory2Mapper.selectList(new QueryWrapper<BaseCategory2>().eq("category1_id", category1Id));
    }

    @Override
    public List<BaseCategory3> getCategory3(Long category2Id) {
        return baseCategory3Mapper.selectList(new QueryWrapper<BaseCategory3>().eq("category2_id", category2Id));
    }

    // 返回的数据复杂，baseMapper不能处理
    // 分析业务，即两张表关联查询，商品属性表和商品属性值表相关联，
    // base_attr_info.id和base_attr_value.attr_id
    //查询条件是分类id和分类层级
    @Override
    public List<BaseAttrInfo> getAttrInfoList(Long category1Id, Long category2Id, Long category3Id) {
        List<BaseAttrInfo> attrInfoList = baseAttrInfoMapper.getAttrInfoList(category1Id, category2Id, category3Id);
        return attrInfoList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        // 修改还是保存
        // id为null就保存,否则修改
        if (StringUtils.isEmpty(baseAttrInfo.getId())) {
            baseAttrInfoMapper.insert(baseAttrInfo);
        } else {
            baseAttrInfoMapper.updateById(baseAttrInfo);
        }
        QueryWrapper<BaseAttrValue> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("attr_id", baseAttrInfo.getId());
        // 先删除baseAttrValue表中的相关数据，再重新添加
        baseAttrValueMapper.delete(queryWrapper);
        // 取出baseAttrInfo中的平台属性值集合
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        // 遍历集合，添加数据
        // 验证数据
        if (attrValueList != null && attrValueList.size() > 0) {
            for (BaseAttrValue baseAttrValue : attrValueList) {
                baseAttrValue.setAttrId(baseAttrInfo.getId());
                // 插入数据
                baseAttrValueMapper.insert(baseAttrValue);
            }
        }
    }

    @Override
    public BaseAttrInfo getAttrInfo(Long attrInfoId) {
        List<BaseAttrValue> baseAttrValueList = baseAttrValueMapper
                .selectList(new QueryWrapper<BaseAttrValue>().eq("attr_id", attrInfoId));
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrInfoId);
        baseAttrInfo.setAttrValueList(baseAttrValueList);
        return baseAttrInfo;
    }

    @Override
    public IPage<SpuInfo> getSpuInfoPage(Page<SpuInfo> spuInfoPage, SpuInfo spuInfo) {
        QueryWrapper queryWrapper = new QueryWrapper();
        Long category3Id = spuInfo.getCategory3Id();
        // 查询条件
        queryWrapper.eq("category3_id", category3Id);
        // 排序
        queryWrapper.orderByDesc("id");
        IPage iPage = baseMapper.selectPage(spuInfoPage, queryWrapper);
        return iPage;
    }

    @Override
    public List<BaseSaleAttr> getBaseSaleAttrList() {
        return baseSaleAttrMapper.selectList(null);
    }

    @Override
    public void saveSpuInfo(SpuInfo spuInfo) {
        // 插入数据到spu_info表中，主键回填
        spuInfoMapper.insert(spuInfo);
        // 插入商品图片spu_image
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if (!CollectionUtils.isEmpty(spuImageList)) {
            for (SpuImage spuImage : spuImageList) {
                // 插入回填的spuid
                spuImage.setSpuId(spuInfo.getId());
                spuImageMapper.insert(spuImage);
            }
        }
        // spuSaleAttrList 插入spu商品销售数据和spu商品销售数据值spuSaleAttrValueList
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if (!CollectionUtils.isEmpty(spuSaleAttrList)) {
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);
                // 插入销售属性值
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
                if (!CollectionUtils.isEmpty(spuSaleAttrValueList)) {
                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                        // 插入spuinfo中的id和上一级中的spuSaleAttr中的数据
                        spuSaleAttrValue.setSpuId(spuInfo.getId());
                        spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());
                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);
                    }
                }
            }
        }

    }

    @Override
    public List<SpuImage> getSpuImageList(Long spuId) {
        return spuImageMapper.selectList(new QueryWrapper<SpuImage>().eq("spu_id", spuId));
    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(Long spuId) {
        // 这里利用了mybatis的好处，将一对多的关系封装进数据中
        List<SpuSaleAttr> spuSaleAttrList = spuSaleAttrMapper.getSpuSaleAttrList(spuId);
        return spuSaleAttrList;
    }

    @Override
    public void saveSkuInfo(SkuInfo skuInfo) {
        // 插入相关数据，回填主键
        skuInfoMapper.insert(skuInfo);
//        先插入图片
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if (!CollectionUtils.isEmpty(skuImageList)) {
            for (SkuImage skuImage : skuImageList) {
                // 回填的主键关联图片表
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }
        // 平台属性值集合
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if (!CollectionUtils.isEmpty(skuAttrValueList)) {
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }
        // 销售属性值集合插入
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        if (!CollectionUtils.isEmpty(skuSaleAttrValueList)) {
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
                // 这里需要插入两个值，这样更加方便以后的操作
                skuSaleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId());
                skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }
    }

    @Override
    public IPage<SkuInfo> index(Page<SkuInfo> skuInfoPage) {
        QueryWrapper<SkuInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");
        IPage<SkuInfo> iPage = skuInfoMapper.selectPage(skuInfoPage, queryWrapper);
        return iPage;
    }

    @Override
    public void onSale(Long skuId) {
        // 其实就是修改is_sale的值
        // update sku_info set is_sale = 1 where id = #{skuId}
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(1);
        skuInfoMapper.updateById(skuInfo);
    }

    @Override
    public void cancelSale(Long skuId) {
        // 其实就是修改is_sale的值
        // update sku_info set is_sale = 1 where id = #{skuId}
        // updateById不是全部修改，而是设置了什么值就修改什么值
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(0);
        skuInfoMapper.updateById(skuInfo);
    }

    @GmallCache(prefix = RedisConst.SKUKEY_PREFIX)
    @Override
    public SkuInfo getSkuInfo(Long skuId) {
        // 1、查询基本信息
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        // 2、查询图片集合
        QueryWrapper<SkuImage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("sku_id", skuId);
        List<SkuImage> skuImages = skuImageMapper.selectList(queryWrapper);
        // 防止空指针异常
        if (skuInfo != null) {
            skuInfo.setSkuImageList(skuImages);
        }
        return skuInfo;
    }

    @GmallCache(prefix = "categoryViewByCategory3Id:")
    @Override
    public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {
        return baseCategoryViewMapper.selectById(category3Id);
    }

    @GmallCache(prefix = "skuPrice:")
    @Override
    public BigDecimal getSkuPrice(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        // 做个验证，防止出现空指针异常
        if (null != skuInfo) {
            return skuInfo.getPrice();
        }
        return new BigDecimal(0);
    }

    @GmallCache(prefix = "spuSaleAttrListCheckBySku:")
    @Override
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {
        return spuSaleAttrMapper.getSpuSaleAttrListCheckBySku(skuId, spuId);
    }

    /**
     * 根据spuId获取销售属性组合与skuid关联map
     *
     * @param spuId
     * @return
     */
    @GmallCache(prefix = "skuValueIdsMap:")
    @Override
    public Map getSkuValueIdsMap(Long spuId) {
        Map<String, Object> map = new HashMap<>();
        // 没有合适的数据类接收，使用map
        List<Map> mapList = skuSaleAttrValueMapper.selectSaleAttrValuesBySpu(spuId);
        if (!CollectionUtils.isEmpty(mapList)) {
            for (Map map1 : mapList) {
                Object sku_id = map1.get("sku_id");
                String value_ids = (String) map1.get("value_ids");
                map.put(value_ids, sku_id);
            }
        }
        return map;
    }

    @Override
    public List<JSONObject> getBaseCategoryList() {
        List<JSONObject> list = new ArrayList<>();
        // 1、获取有分类信息
        List<BaseCategoryView> baseCategoryViews = baseCategoryViewMapper.selectList(null);
        // 利用stream流，循环上面的集合并安一级分类Id 进行分组
        Map<Long, List<BaseCategoryView>> category1Map = baseCategoryViews.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));
        // 设置初始化下标index
        int index = 1;
        // 遍历第一种方法，使用迭代器
        Iterator<Map.Entry<Long, List<BaseCategoryView>>> iterator1 = category1Map.entrySet().iterator();
        while (iterator1.hasNext()) {
            Map.Entry<Long, List<BaseCategoryView>> entry1 = iterator1.next();
            // 取出key，value,1对多关系，有多个二级分类信息
            Long category1Id = entry1.getKey();
            List<BaseCategoryView> category2List = entry1.getValue();
            JSONObject category1 = new JSONObject();
            category1.put("index", index);
            category1.put("categoryId", category1Id);
            // 分组后，每个二级分类信息里的1级分类name是一样的
            category1.put("categoryName", category2List.get(0).getCategory1Name());
            // 初始下标迭代
            index++;
            // 2、利用stream流对二级分类信息进行分组
            Map<Long, List<BaseCategoryView>> category2Map = category2List.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
            // 1包2，2包3，json数据有分级关系
            // 声明二级分类对象集合
            List<JSONObject> category2Child  = new ArrayList<>();
            // // 遍历第二种方法，使用增强for
            for(Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()){
                Long category2Id = entry2.getKey();
                List<BaseCategoryView> category3List = entry2.getValue();
                // 单个二级分类信息
                JSONObject category2 = new JSONObject();
                category2.put("categoryId",category2Id);
                category2.put("categoryName",category3List.get(0).getCategory2Name());
                // 声明3级分类对象集合
                List<JSONObject> category3Child  = new ArrayList<>();
                // 遍历3级分类信息
                category3List.stream().forEach(category3View -> {
                    JSONObject category3 = new JSONObject();
                    Long category3Id = category3View.getCategory3Id();
                    String category3Name = category3View.getCategory3Name();
                    category3.put("categoryId",category3Id);
                    category3.put("categoryName",category3Name);
                    category3Child.add(category3);
                });
                category2.put("categoryChild",category3Child);
                // 将三级数据放入二级里面
                category2Child.add(category2);
            }
            // 将二级级数据放入一级里面
            category1.put("categoryChild",category2Child);
            list.add(category1);
        }
        return list;
    }

    @Override
    public BaseTrademark getTrademark(Long tmId) {
        BaseTrademark baseTrademark = baseTrademarkMapper.selectById(tmId);
        return baseTrademark;
    }

    @Override
    public List<BaseAttrInfo> getAttrList(Long skuId) {
        return baseAttrInfoMapper.selectAttrList(skuId);
    }

}
