package com.carl.mall.user.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * real 模式种子数据入口：复用 SeedService（D2 决策：RealDataInitializer 与 mock 播种一致，
 * 首次连 MySQL 空库自动播种 4 账号 + 默认地址；幂等，重复启动不重复插入）。
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
