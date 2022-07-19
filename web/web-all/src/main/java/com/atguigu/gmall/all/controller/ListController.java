package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchParam;
import com.sun.org.apache.bcel.internal.generic.NEW;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ListController {

    @Autowired
    private ListFeignClient listFeignClient;

    @GetMapping("list.html")
    public String search(SearchParam searchParam, Model model) {
        Result<Map> result = listFeignClient.list(searchParam);

        // 记录拼接的url，查询条件有回显到地址栏
        String urlParam = makeUrlParam(searchParam);
        model.addAttribute("urlParam", urlParam);
        // 处理品牌条件回显,面包屑
        String trademarkParam = makeTrademark(searchParam.getTrademark());
        model.addAttribute("trademarkParam", trademarkParam);
        // 处理平台属性回显面包屑，平台属性有多个
        List<Map<String, String>> propsParamList = makeProps(searchParam.getProps());
        model.addAttribute("propsParamList", propsParamList);
        // 处理排序，最终显示
        Map<String, Object> orderMap = dealOrder(searchParam.getOrder());
        model.addAttribute("orderMap",orderMap);
        model.addAllAttributes(result.getData());
        return "list/index";
    }

    /**
     * 显示商品信息
     *
     * @param order
     * @return
     */
    private Map<String, Object> dealOrder(String order) {
        Map<String, Object> map = new HashMap<>();
        if (!StringUtils.isEmpty(order)) {
            // &order=2:desc
            String[] split = order.split(":");
            if (split != null & split.length == 2) {
                // 1是热度，2是价格
                map.put("type", split[0]);
                map.put("sort", split[1]);
            }
        }else {
            map.put("type", "1");
            map.put("sort", "desc");
        }
        return map;
    }

    /**
     * 前端需要在用户点击筛选后出现面包屑，需要封装面包屑信息返回 props=106:苹果手机:手机系统
     *
     * @param props
     * @return
     */
    private List<Map<String, String>> makeProps(String[] props) {
        List<Map<String, String>> list = new ArrayList<>();
        if (props != null && props.length != 0) {
            for (String prop : props) {
                String[] split = prop.split(":");
                if (split != null && split.length == 3) {
                    Map<String, String> map = new HashMap<>();
                    map.put("attrId", split[0]);
                    map.put("attrName", split[2]);
                    map.put("attrValue", split[1]);
                    list.add(map);
                }
            }
        }
        return list;
    }

    /**
     * 前端需要在用户点击筛选后出现面包屑，需要封装面包屑信息返回
     *
     * @param trademark
     * @return
     */
    private String makeTrademark(String trademark) {
        if (!StringUtils.isEmpty(trademark)) {
            // trademark=2:苹果
            String[] split = trademark.split(":");
            if (split != null && split.length == 2) {
                // 返回面包屑
                return "品牌：" + split[1];
            }
        }
        // 用户没有点击品牌就不显示
        return "";
    }

    /**
     * 根据前端传递过来的搜索条件拼接地址栏信息
     *
     * @param searchParam
     * @return
     */
    private String makeUrlParam(SearchParam searchParam) {
        StringBuffer urlParam = new StringBuffer();
        // 初始情况下
        if (searchParam.getKeyword() != null) {
            urlParam.append("keyword=").append(searchParam.getKeyword());
        }
        if (searchParam.getCategory1Id() != null) {
            urlParam.append("category1Id=").append(searchParam.getCategory1Id());
        }
        if (searchParam.getCategory2Id() != null) {
            urlParam.append("category1Id=").append(searchParam.getCategory2Id());
        }
        if (searchParam.getCategory3Id() != null) {
            urlParam.append("category3Id=").append(searchParam.getCategory3Id());
        }
        // 用户点击品牌后
        if (searchParam.getTrademark() != null) {
            // 分类id检索和搜索框检索的信息存在
            if (urlParam.length() > 0) {
                urlParam.append("&trademark=").append(searchParam.getTrademark());
            }
        }
        // 用户点击平台属性检索后
        String[] props = searchParam.getProps();
        if (props != null) {
            // 因为是一组条件
            for (String prop : props) {
                if (urlParam.length() > 0) {
                    urlParam.append("&props=").append(prop);
                }
            }
        }
        return "list.html?" + urlParam.toString();
    }
}
