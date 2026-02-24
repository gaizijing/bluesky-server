package com.bluesky;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 低空气象飞行保障服务系统 - 主启动类
 *
 * @author BlueSky Team
 * @version 1.0.0
 */
@SpringBootApplication
@MapperScan("com.bluesky.mapper")
public class BlueSkyApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlueSkyApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("  BlueSky Server Started Successfully!");
        System.out.println("  Swagger UI: http://localhost:8080/api/swagger-ui.html");
        System.out.println("  API Docs: http://localhost:8080/api/v3/api-docs");
        System.out.println("========================================\n");
    }
}
