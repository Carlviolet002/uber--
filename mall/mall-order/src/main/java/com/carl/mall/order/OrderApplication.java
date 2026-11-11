package com.carl.mall.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类（端口 8083）
 */
@SpringBootApplication(scanBasePackages = "com.carl.mall")
@MapperScan("com.carl.mall.order.mapper")
@EnableScheduling
@EnableRabbit
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
