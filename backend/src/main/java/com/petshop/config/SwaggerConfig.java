package com.petshop.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 配置类
 * 用于生成API文档
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("宠物用品商城 API")
                        .version("1.0.0")
                        .description("宠物用品商城后端API文档")
                        .termsOfService("https://petshop.com/terms")
                        .license(
                                new License()
                                        .name("Apache 2.0")
                                        .url("https://springdoc.org")
                        )
                );
    }
}
