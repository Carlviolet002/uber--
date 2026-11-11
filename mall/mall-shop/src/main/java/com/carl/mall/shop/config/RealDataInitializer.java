package com.carl.mall.shop.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * real 模式种子数据入口：复用 SeedService（3 店 × 2 分类 × 9 菜品 + owner_user_id 填充）。
 * 幂等：selectCount==0 才插入，重复启动不重复播种。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class RealDataInitializer implements CommandLineRunner {

    private final SeedService seedService;

    @Override
    public void run(String... args) {
        seedService.seed();
    }
}
