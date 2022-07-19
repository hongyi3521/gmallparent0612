package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.client.ProductFeignClient;
import lombok.SneakyThrows;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.swing.text.Highlighter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Override
    public void upperGoods(Long skuId) {
        // 创建Goods对象，接收数据
        Goods goods = new Goods();
        // 通过skuId 来查询平台属性数据
        // supplyAsync有返回值
        CompletableFuture<Void> searchAttrListFuture = CompletableFuture.runAsync(() -> {
            List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuId);
            if (attrList != null) {
                // 遍历
                List<SearchAttr> searchAttrList = attrList.stream().map(baseAttrInfo -> {
                    SearchAttr searchAttr = new SearchAttr();
                    searchAttr.setAttrId(baseAttrInfo.getId());
                    searchAttr.setAttrName(baseAttrInfo.getAttrName());
                    List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
                    // 一个sku只一个平台属性对应一个属性值
                    searchAttr.setAttrValue(attrValueList.get(0).getValueName());
                    return searchAttr;
                }).collect(Collectors.toList());
                goods.setAttrs(searchAttrList);
            }
        }, threadPoolExecutor);
        //查询sku信息
        CompletableFuture<SkuInfo> skuInfoFuture = CompletableFuture.supplyAsync(() -> {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            goods.setDefaultImg(skuInfo.getSkuDefaultImg());
            goods.setPrice(skuInfo.getPrice().doubleValue());
            goods.setId(skuInfo.getId());
            goods.setTitle(skuInfo.getSkuName());
            return skuInfo;
        }, threadPoolExecutor);
        // 查询品牌信息
        CompletableFuture<Void> trademarkFuture = skuInfoFuture.thenAcceptAsync(skuInfo -> {
            BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
            if (trademark != null) {
                goods.setTmId(trademark.getId());
                goods.setTmName(trademark.getTmName());
                goods.setTmLogoUrl(trademark.getLogoUrl());
            }
        }, threadPoolExecutor);
        // 查询分类信息
        CompletableFuture<Void> baseCategoryViewFuture = skuInfoFuture.thenAcceptAsync(skuInfo -> {
            BaseCategoryView baseCategoryView = productFeignClient.getCategoryViewByCategory3Id(skuInfo.getCategory3Id());
            if (baseCategoryView != null) {
                goods.setCategory1Id(baseCategoryView.getCategory1Id());
                goods.setCategory1Name(baseCategoryView.getCategory1Name());
                goods.setCategory2Id(baseCategoryView.getCategory2Id());
                goods.setCategory2Name(baseCategoryView.getCategory2Name());
                goods.setCategory3Id(baseCategoryView.getCategory3Id());
                goods.setCategory3Name(baseCategoryView.getCategory3Name());
            }
        }, threadPoolExecutor);
        // 启动线程
        CompletableFuture.allOf(
                searchAttrListFuture,
                skuInfoFuture,
                trademarkFuture,
                baseCategoryViewFuture
        ).join();
        // 添加索引到es中
        goodsRepository.save(goods);
    }

    /**
     * 根据商品id下架商品
     *
     * @param skuId
     */
    @Override
    public void lowerGoods(Long skuId) {
        goodsRepository.deleteById(skuId);
    }

    @Override
    public void incrHotScore(Long skuId) {
        // 商品热度信息存在redis中实时更新，达到一定规则后，更新es中的信息
        // 热度是商品排序的一个标准
        // 定义redis存储数据的key
        String hotKey = "hotScore";
        // 使用redis高级客户端，zset数据格式,key值，key里的键值对步长
        Double hotScore = redisTemplate.opsForZSet().incrementScore(hotKey, "skuId:" + skuId, 1);
        // 如果热度增加10，就更新es
        if (hotScore % 10 == 0) {
            // 先查再改
            Optional<Goods> optional = goodsRepository.findById(skuId);
            Goods goods = optional.get();
            // double数据需要转换
            goods.setHotScore(Math.round(hotScore));
            goodsRepository.save(goods);
        }
    }

    // 复杂查询使用
    @Autowired
    private RestHighLevelClient restHighLevelClient;


    @SneakyThrows
    @Override
    public SearchResponseVo search(SearchParam searchParam){
        // 1、通过查询条件生成ES的dsl语句
        SearchRequest searchRequest = this.buildQueryDsl(searchParam);
        // 2、es的高级客户端通过dsl语句查询es获取结果集
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(searchResponse);
        // 3、取出es查询结果集数据，封装进SearchResponseVo中返回
        SearchResponseVo searchResponseVo = parseSearchResult(searchResponse);
        // 设置当前页
        searchResponseVo.setPageNo(searchParam.getPageNo());
        // 设置每页最大数据数
        searchResponseVo.setPageSize(searchParam.getPageSize());
        // 设置总页数
        Long totalPages = (searchResponseVo.getTotal() +
                searchResponseVo.getPageSize() - 1) / searchResponseVo.getPageSize();
        searchResponseVo.setTotalPages(totalPages);
        return searchResponseVo;
    }

    /**
     * 封装结果集
     *
     * @param response
     * @return
     */
    private SearchResponseVo parseSearchResult(SearchResponse response) {
        // 声明返回对象
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        // es中取出的数据进行封装
        SearchHits hits = response.getHits();
        // 商品信息集合
        List<Goods> goodsList = new ArrayList<>();
        SearchHit[] subHits = hits.getHits();
        if (subHits != null && subHits.length > 0) {
            for (SearchHit subHit : subHits) {
                Goods goods = JSONObject.parseObject(subHit.getSourceAsString(), Goods.class);
                // 获取高亮title
                if (subHit.getHighlightFields().get("title") != null) {
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    goods.setTitle(title.toString());
                }
                goodsList.add(goods);
            }
        }
        searchResponseVo.setGoodsList(goodsList);
        // 获取聚会的整体数据
        Map<String, Aggregation> asMap = response.getAggregations().getAsMap();
        // 品牌信息集合
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) asMap.get("tmIdAgg");
        List<? extends Terms.Bucket> tmIdAggBuckets = tmIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(tmIdAggBuckets)) {
            List<SearchResponseTmVo> trademarkList = tmIdAggBuckets.stream().map(bucket -> {
                // 设置每个品牌的数据集合bean
                SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
                // 设置品牌id
                // searchResponseTmVo.setTmId(bucket.getKeyAsNumber().longValue());
                searchResponseTmVo.setTmId(Long.parseLong(bucket.getKeyAsString()));
                ParsedStringTerms tmNameAgg = bucket.getAggregations().get("tmNameAgg");
                String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
                // 设置品牌名字
                searchResponseTmVo.setTmName(tmName);
                // 设置品牌logourl
                ParsedStringTerms tmLogoUrlAgg = bucket.getAggregations().get("tmLogoUrlAgg");
                String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
                searchResponseTmVo.setTmLogoUrl(tmLogoUrl);
                return searchResponseTmVo;
            }).collect(Collectors.toList());
            searchResponseVo.setTrademarkList(trademarkList);
        }
        // 平台属性集合
        ParsedNested attrAgg = (ParsedNested) asMap.get("attrAgg");
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        // 获取桶
        List<? extends Terms.Bucket> buckets = attrIdAgg.getBuckets();
        // 利用stream流
        if (!CollectionUtils.isEmpty(buckets)) {
            List<SearchResponseAttrVo> attrsList = buckets.stream().map(bucket -> {
                // 创建平台属性对象
                SearchResponseAttrVo attrVo = new SearchResponseAttrVo();
                long attrId = bucket.getKeyAsNumber().longValue();
                attrVo.setAttrId(attrId);
                // 大聚合里的小聚合
                ParsedStringTerms attrNameAgg = bucket.getAggregations().get("attrNameAgg");
                // 只有一个商品属性名所以只获取一个
                String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
                attrVo.setAttrName(attrName);
                ParsedStringTerms attrValueAgg = bucket.getAggregations().get("attrValueAgg");
                List<? extends Terms.Bucket> valueAggBuckets = attrValueAgg.getBuckets();
                // 有多个商品属性值，所以要遍历
                List<String> attrValues = new ArrayList<>();
                for (Terms.Bucket valueAggBucket : valueAggBuckets) {
                    String attrValue = valueAggBucket.getKeyAsString();
                    attrValues.add(attrValue);
                }
                attrVo.setAttrValueList(attrValues);
                return attrVo;
            }).collect(Collectors.toList());
            searchResponseVo.setAttrsList(attrsList);
        }
        // 设置总记录数
        searchResponseVo.setTotal(hits.totalHits);
        return searchResponseVo;
    }

    /**
     * 生成dsl语句
     *
     * @param searchParam
     * @return
     */
    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        // 根据kibana里写的dsl语句，写java代码
        // 1、构建查询器
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        // 2、构建boolquery查询器
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        // 逐个把语句写入bool中
        // 搜索框检索keyword
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            // 要求是检索框的内容分词，必须同时符合
            MatchQueryBuilder title = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.AND);
            boolQueryBuilder.must(title);
        }
        // 品牌条件过滤 trademark=3:华为
        String trademark = searchParam.getTrademark();
        if (!StringUtils.isEmpty(trademark)) {
            // 通过split分割传过来的数据
            String[] split = trademark.split(":");
            if (split != null && split.length == 2) {
                TermQueryBuilder tmId = QueryBuilders.termQuery("tmId", split[0]);
                boolQueryBuilder.filter(tmId);
            }
        }
        // 分类id检索
        Long category1Id = searchParam.getCategory1Id();
        if (!StringUtils.isEmpty(category1Id)) {
            TermQueryBuilder categroy1Id = QueryBuilders.termQuery("category1Id", category1Id);
            boolQueryBuilder.filter(categroy1Id);
        }
        Long category2Id = searchParam.getCategory2Id();
        if (!StringUtils.isEmpty(category2Id)) {
            TermQueryBuilder categroy2Id = QueryBuilders.termQuery("category1Id", category2Id);
            boolQueryBuilder.filter(categroy2Id);
        }
        Long category3Id = searchParam.getCategory3Id();
        if (!StringUtils.isEmpty(category3Id)) {
            TermQueryBuilder categroy3Id = QueryBuilders.termQuery("category3Id", category3Id);
            boolQueryBuilder.filter(categroy3Id);
        }
        // 平台属性条件查询 props=106:安卓手机:手机系统&props=24:128G:机身内存
        String[] props = searchParam.getProps();
        if (props != null && props.length > 0) {
            for (String prop : props) {
                String[] split = prop.split(":");
                if (split.length == 3 && split != null) {
                    // 构建嵌套查询,即大条件里包含小条件
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    // 子查询
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    TermQueryBuilder attrIdQuery = QueryBuilders.termQuery("attrs.attrId", split[0]);
                    TermQueryBuilder attrValueQuery = QueryBuilders.termQuery("attrs.attrValue", split[1]);
                    TermQueryBuilder attrNameQuery = QueryBuilders.termQuery("attrs.attrName", split[2]);
                    subBoolQuery.must(attrIdQuery);
                    subBoolQuery.must(attrValueQuery);
                    subBoolQuery.must(attrNameQuery);
                    // 小条件插入大条件
                    NestedQueryBuilder attrs = QueryBuilders.nestedQuery("attrs", subBoolQuery, ScoreMode.None);
                    boolQuery.must(attrs);
                    // 将每个平台属性条件添加到整个查询器中
                    boolQueryBuilder.must(boolQuery);
                }
            }
        }
        // 执行条件查询
        searchSourceBuilder.query(boolQueryBuilder);
        // 分页
        // 每页显示数
        Integer pageSize = searchParam.getPageSize();
        // 确定起始页
        int from = (searchParam.getPageNo() - 1) * pageSize;
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(pageSize);
        // 排序 order=1:desc 1是热度，2是价格
        String order = searchParam.getOrder();
        if (!StringUtils.isEmpty(order)) {
            String[] split = order.split(":");
            if (split != null && split.length == 2) {
                String field = null;
                switch (split[0]) {
                    case "1":
                        field = "hotScore";
                        break;
                    case "2":
                        field = "price";
                        break;
                }
                searchSourceBuilder.sort(field, "asc".equals(split[1]) ? SortOrder.ASC : SortOrder.DESC);
            } else {
                // 传值不符合规则，默认
                searchSourceBuilder.sort("hotScore", SortOrder.DESC);
            }
        }
        // 高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.preTags("<span style=color:red>");
        highlightBuilder.postTags("</span>");
        // 传入大查询中
        searchSourceBuilder.highlighter(highlightBuilder);

        // 根据品牌id
        TermsAggregationBuilder tmAggBulider = AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));
        searchSourceBuilder.aggregation(tmAggBulider);
        // 平台属性聚会分组
        NestedAggregationBuilder nestedAggregationBuilder = AggregationBuilders.nested("attrAgg", "attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))
                );
        searchSourceBuilder.aggregation(nestedAggregationBuilder);
        // 设置结果集过滤，即设置显示goods的哪些字段
        searchSourceBuilder.fetchSource(new String[]{"id", "defaultImg", "title", "price"}, null);
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        System.out.println("dsl : " + searchSourceBuilder.toString());
        return searchRequest;
    }
}
