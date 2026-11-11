package com.carl.mall.shop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商家服务启动类（端口 8082）
 */
@SpringBootApplication(scanBasePackages = "com.carl.mall")
@MapperScan("com.carl.mall.shop.mapper")
public class MallShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallShopApplication.class, args);
    }
}
