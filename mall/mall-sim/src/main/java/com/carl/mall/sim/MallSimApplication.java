package com.carl.mall.sim;

import com.carl.mall.sim.config.SimBeanConfig;
import com.carl.mall.sim.engine.SimulationEngine;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 仿真器入口（非 Web 应用，spring.main.web-application-type=none）。
 *
 * <p>只扫描 com.carl.mall.sim（不扫 com.carl.mall，避免把 dispatch 服务组件引入），
 * 通过 @Import(SimBeanConfig.class) 显式装配三个派单策略 bean 与 MockGeoIndex。</p>
 */
@SpringBootApplication(scanBasePackages = "com.carl.mall.sim")
@Import(SimBeanConfig.class)
public class MallSimApplication implements CommandLineRunner {

    private final SimulationEngine engine;

    public MallSimApplication(SimulationEngine engine) {
        this.engine = engine;
    }

    public static void main(String[] args) {
        SpringApplication.run(MallSimApplication.class, args);
    }

    @Override
    public void run(String... args) {
        engine.run();
    }
}
