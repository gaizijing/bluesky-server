package com.bluesky.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 3 文档配置
 * <p>
 * 访问地址：http://localhost:8080/api/doc.html
 * </p>
 */
@Configuration
public class Knife4jConfig {

    private static final String SECURITY_SCHEME_NAME = "Authorization";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // 文档基础信息
                .info(new Info()
                        .title("低空气象飞行保障服务系统 API")
                        .description("接口说明：\n" +
                                "- 服务端口：8080，统一前缀：/api\n" +
                                "- 认证方式：JWT Bearer Token\n" +
                                "- 登录接口无需 Token，其余接口请在右上角 Authorize 中填入：Bearer <token>\n" +
                                "- 统一响应格式：{ code, message, data }")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("BlueSky Team")
                                .email("support@bluesky.com"))
                        .license(new License()
                                .name("Apache 2.0")))
                // 全局安全方案：JWT Bearer
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("填入登录接口返回的 token，无需加 'Bearer ' 前缀")));
    }
}
