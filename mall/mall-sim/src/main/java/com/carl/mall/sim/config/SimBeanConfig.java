package com.carl.mall.sim.config;

import com.carl.mall.common.geo.GeoIndex;
import com.carl.mall.common.geo.MockGeoIndex;
import com.carl.mall.dispatch.strategy.GeoNearestStrategy;
import com.carl.mall.dispatch.strategy.HungarianBatchStrategy;
import com.carl.mall.dispatch.strategy.NearestStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 仿真器专用 Bean 装配。
 *
 * <p>sim 不扫描 com.carl.mall.dispatch 包（避免引入 Web/MQ/持久化组件），
 * 因此在这里显式 @Bean 三个派单策略与内存 GEO 索引。
 * 注意：@Bean 方法返回的实例同样会被 Spring 处理后置注入，
 * 所以 HungarianBatchStrategy 的 @Value 权重字段（mall.dispatch.weights.*）
 * 能解析 sim 的 application.yml 中配置的权重键。</p>
 */
@Configuration
public class SimBeanConfig {

    @Bean
    public NearestStrategy nearestStrategy() {
        return new NearestStrategy();
    }

    @Bean
    public GeoNearestStrategy geoNearestStrategy() {
        return new GeoNearestStrategy();
    }

    @Bean
    public HungarianBatchStrategy hungarianBatchStrategy() {
        return new HungarianBatchStrategy();
    }

    @Bean
    public GeoIndex simGeoIndex() {
        return new MockGeoIndex();
    }
}
