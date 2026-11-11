package com.carl.mall.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 事件发布：无 MQ 时仅记录日志（跨服务解耦通过 HTTP 轮询/直调兜底）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockEventPublisher implements EventPublisher {

    @Override
    public void publish(String routingKey, Object payload) {
        log.info("[MOCK-MQ] routingKey={} payload={}", routingKey, payload);
    }

    @Override
    public void publishDelayed(String routingKey, Object payload, long delayMinutes) {
        // mock 模式无 MQ，超时关单由 OrderTimeoutService 扫描器兜底，这里仅记录
        log.info("[MOCK-MQ] delayed routingKey={} delayMinutes={} payload={}", routingKey, delayMinutes, payload);
    }
}
