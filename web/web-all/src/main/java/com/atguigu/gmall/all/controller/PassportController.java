package com.atguigu.gmall.all.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * 网关过滤跳转到这里，用于跳转到登陆页面后带有之前的地址
 */
@Controller
public class PassportController {

    @GetMapping("login.html")
    public String login(HttpServletRequest request){
        // 网关判定需要登陆，就把用户的地址记录跳转到这里，这里在转到登录页面
        String originUrl = request.getParameter("originUrl");
        request.setAttribute("originUrl",originUrl);
        return "login";
    }

}
