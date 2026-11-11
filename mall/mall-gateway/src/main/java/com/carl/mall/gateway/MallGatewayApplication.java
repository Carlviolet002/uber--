package com.carl.mall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关服务启动类
 * scanBasePackages 必须为 com.carl.mall，以扫描 mall-common 中的 JwtProperties 等 Bean
 */
@SpringBootApplication(scanBasePackages = "com.carl.mall")
public class MallGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallGatewayApplication.class, args);
    }
}
