package com.carl.mall.dispatch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 派单服务启动类（毕设研究主体）
 *
 * <p>扫描 com.carl.mall 全包：使 mall-common 中按 mall.mock.enabled 条件装配的
 * Mock/Redis GeoIndex、Mock/Rabbit EventPublisher、GlobalExceptionHandler 等生效。</p>
 */
@SpringBootApplication(scanBasePackages = "com.carl.mall")
@MapperScan("com.carl.mall.dispatch.mapper")
@EnableScheduling
@EnableRabbit
public class MallDispatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallDispatchApplication.class, args);
    }
}
