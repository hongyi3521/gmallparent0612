package com.atguigu.gmall.gateway.fillter;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter {
    @Autowired
    private RedisTemplate redisTemplate;

    // 匹配路径的工具类
    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Value("${authUrls.url}")
    private String authUrls;

    /**
     * 网关统一对用户访问的地址进行管理
     * 这里判断用户是否登陆，如果没有登录就跳转登录
     * 如果地址有问题就，返回失败结果
     *
     * @param exchange
     * @param chain
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取请求对象
        ServerHttpRequest request = exchange.getRequest();
        // 获取响应对象
        ServerHttpResponse response = exchange.getResponse();
        // 获取请求地址
        String path = request.getURI().getPath();
        // 对地址进行判断
        // 1、如果是内部接口，则网关拦截不允许外部访问！带有inner代表是api，微服务互相调用接口，内部接口
        if (antPathMatcher.match("/**/inner/**", path)) {
            return out(response, ResultCodeEnum.PERMISSION);// 没有权限
        }
        // 2、根据请求对象，获取用户对象id
        String userId = this.getUserId(request);
        // getUserId方法里还需要验证用户登录的地址和redis的ip地址是否一致
        // 不一致可能是盗用token,需要处理
        if ("-1".equals(userId)) {
            return out(response, ResultCodeEnum.PERMISSION);
        }
        // 3、api接口，异步请求，校验用户必须登录
        if (antPathMatcher.match("/api/**/auth/**", path)) {
            // localhost请求没有token，userid就是null，
            if(StringUtils.isEmpty(userId)) {
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }

        // 4、验证用户访问一些功能接口，程序需要自动跳转到登录页面
        // trade.html,myOrder.html,list.html
        String[] split = authUrls.split(",");
        // 请求地址包含了里面的字段需要程序跳转
        for (String authUrl : split) {
            // 请求的地址需要登录，但是request里的id为空，即跳转登录
            if (path.indexOf(authUrl) != -1 && StringUtils.isEmpty(userId)) {
                // 303状态码表示由于请求对应的资源存在着另一个URI，应使用重定向获取请求的资源
                response.setStatusCode(HttpStatus.SEE_OTHER);
                response.getHeaders().set(HttpHeaders.LOCATION, "http://www.gmall.com/login.html?originUrl=" + request.getURI());
                // 跳转页面,重定向到登录页面
                return response.setComplete();
            }
        }
        // 获取临时用户id，记录id，以便购物车使用
        String userTempId = this.getUserTempId(request);

        // 用户id不为空，传递到后端，走正常业务请求
        if (!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)) {
            if(!StringUtils.isEmpty(userId)){
                request.mutate().header("userId", userId).build();
            }
            if(!StringUtils.isEmpty(userTempId)){
                request.mutate().header("userTempId", userTempId).build();
            }
            // 将现在的request 变成 exchange对象
            return chain.filter(exchange.mutate().request(request).build());
        }
        return chain.filter(exchange);
    }

    // 接口鉴权失败返回数据
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum loginAuth) {
        // 返回用户没有权限登录
        Result<Object> result = Result.build(null, loginAuth);
        byte[] bits = JSONObject.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer wrap = response.bufferFactory().wrap(bits);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        // 输入到页面
        return response.writeWith(Mono.just(wrap));
    }


    /**
     * 获取临时用户id，生成购物车
     * @param request
     * @return
     */
    private String getUserTempId(ServerHttpRequest request) {
        String userTempId = "";
        List<String> tokemList = request.getHeaders().get("userTempId");
        if(null != tokemList){
            userTempId = tokemList.get(0);
        }else{
            HttpCookie cookie = request.getCookies().getFirst("userTempId");
            if (cookie != null){
                userTempId = URLDecoder.decode(cookie.getValue());
            }
        }
        return userTempId;
    }

    /**
     * 根据请求对象,token,在redis中获取用户id，并验证ip地址
     * @param request
     * @return
     */
    private String getUserId(ServerHttpRequest request) {
        String token = "";
        // 从请求对象中取得token
        List<String> tokenList = request.getHeaders().get("token");
        if (null != tokenList) {
            token = tokenList.get(0);
        } else {
            // header中没有，从cookie中再取,cookie集合中第一个token
            HttpCookie cookie = request.getCookies().getFirst("token");
            if (null != cookie) {
                token = URLDecoder.decode(cookie.getValue());
            }
        }
        // 如果得到了token
        if (!StringUtils.isEmpty(token)) {
            // 从redis中取出token对应的value
//            JSONObject jsonObject = (JSONObject)redisTemplate.opsForValue().get(RedisConst.USER_LOGIN_KEY_PREFIX + token);
            String userStr = (String)redisTemplate.opsForValue().get("user:login:" + token);
            JSONObject jsonObject = JSONObject.parseObject(userStr);
            // 将字符串转成jsonobject
//            JSONObject jsonObject = JSON.parseObject(userStr, JSONObject.class);
            if(jsonObject!=null){
                String ip = jsonObject.getString("ip");
                // 获取现在用户的请求ip
                String ipAddress = IpUtil.getGatwayIpAddress(request);
                if (ip.equals(ipAddress)) {
                    return jsonObject.getString("userId");
                } else {
                    // 如果不一致
                    return "-1";
                }
            }
        }
        return "";
    }
}
