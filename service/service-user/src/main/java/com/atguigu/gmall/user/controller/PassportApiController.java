package com.atguigu.gmall.user.controller;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.IpUtil;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import com.sun.org.apache.regexp.internal.RE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user/passport")
public class PassportApiController {
    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 登录功能，用户把账户和密码发送过过来，服务端进行处理，和响应
     *
     * @return
     */
    @PostMapping("login")
    public Result login(@RequestBody UserInfo userInfo,
                        HttpServletRequest request,
                        HttpServletResponse response) {
        // 1、数据库对用户名和密码进行查验
        UserInfo info = userService.login(userInfo);
        // 2、存在用户，生成token字符串，返回
        if (info != null) {
            String token = UUID.randomUUID().toString().replaceAll("-", "");
            // 创建返回结果
            Map<String, String> map = new HashMap<>();
            map.put("token", token);
            map.put("nickName", info.getNickName());
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("userId", info.getId().toString());
            // 使用工具类获取网络ip
            jsonObject.put("ip", IpUtil.getIpAddress(request));
            // 3、存入数据到redis缓存中,使用String数据结构,key是user:login:+token,value是用户id+用户的地址
            redisTemplate.opsForValue().set(RedisConst.USER_LOGIN_KEY_PREFIX + token,
                    jsonObject.toString(),
                    RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS); // 设置过期时间，7天
            return Result.ok(map);
        } else {
            // 这里出现魔法值，应该统一规范的
            return Result.fail().message("用户名或密码错误");
        }


    }

    /**
     * 退出登录，前端浏览器删除cookie信息，和存储的信息，服务端这里就删除redis里的缓存即可
     *
     * @param request
     * @return
     */
    @GetMapping("logout")
    public Result logout(HttpServletRequest request) {
        // 获取前端传递过来的token
        String token = request.getHeader("token");
        redisTemplate.delete(RedisConst.USER_LOGIN_KEY_PREFIX + token);
        return Result.ok();
    }
}
