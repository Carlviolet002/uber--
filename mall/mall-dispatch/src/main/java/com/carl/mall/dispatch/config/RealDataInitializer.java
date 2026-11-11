package com.carl.mall.dispatch.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * real 模式种子数据入口：复用 SeedService（骑手 id=4/5/6 + 派单配置三条）。
 * 幂等：selectCount==0 才插入，重复启动不重复播种。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class RealDataInitializer implements CommandLineRunner {

    private final SeedService seedService;

    public RealDataInitializer(SeedService seedService) {
        this.seedService = seedService;
    }

    @Override
    public void run(String... args) {
        seedService.seed();
    }
}
