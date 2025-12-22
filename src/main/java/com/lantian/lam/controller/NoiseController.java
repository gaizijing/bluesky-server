package com.lantian.lam.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NoiseController {

    // 浏览器默认
    @RequestMapping({"/favicon.ico", "/favicon"})
    public void favicon(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204
    }

    // 各种系统/浏览器探测
    @RequestMapping("/.well-known/**")
    public void wellKnown(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404
    }

    // 可选：爬虫
    @RequestMapping("/robots.txt")
    public void robots(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204
    }
}
