package com.carl.mall.dispatch.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * mock 模式种子数据入口（薄壳）：复用 SeedService，与 real 模式播种逻辑完全一致。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final SeedService seedService;

    public DataInitializer(SeedService seedService) {
        this.seedService = seedService;
    }

    @Override
    public void run(String... args) {
        seedService.seed();
    }
}
