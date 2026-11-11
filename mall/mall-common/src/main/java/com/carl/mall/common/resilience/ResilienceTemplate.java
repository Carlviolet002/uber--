package com.carl.mall.common.resilience;

import com.carl.mall.common.exception.BizException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 共享韧性模板：熔断(CircuitBreaker) 包 退避重试(Retry)。
 *
 * <p>设计要点（与 order 服务已验证的线上配置保持一致）：
 * <ul>
 *   <li>只识别基础设施异常 {@link RestClientException} 计入熔断 / 触发重试；</li>
 *   <li>业务异常 {@link BizException} 不计入、不重试、直接透传（避免把"商家不存在"误判为故障）；</li>
 *   <li>按 name 维护独立的 CircuitBreaker / Retry 实例，不同客户端 / 接口之间熔断状态隔离；</li>
 *   <li>失败时执行 fallback 降级，避免下游故障拖垮主链路。</li>
 * </ul>
 *
 * <p>Resilience4j 2.x 已移除 <code>Decorators</code> 类，故使用
 * {@code CircuitBreaker.decorateSupplier(cb, Retry.decorateSupplier(retry, supplier))} + 手动 catch 兜底。
 */
@Component
public class ResilienceTemplate {

    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Retry> retries = new ConcurrentHashMap<>();

    private final CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .recordException(e -> e instanceof RestClientException)
            .ignoreExceptions(BizException.class)
            .build();

    private final RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(RestClientException.class)
            .ignoreExceptions(BizException.class)
            .build();

    /**
     * 以熔断包重试的方式执行 supplier；失败时降级到 fallback。
     *
     * @param name     熔断/重试实例名（建议 服务.接口，如 dispatchOrder.pending）
     * @param supplier 受保护的逻辑（如一次 HTTP 调用）
     * @param fallback 最终失败时的降级逻辑（返回空集合 / 抛业务异常均可）
     * @return supplier 结果，或 fallback 结果
     */
    public <T> T run(String name, Supplier<T> supplier, Supplier<T> fallback) {
        CircuitBreaker cb = circuitBreakers.computeIfAbsent(name, n -> CircuitBreaker.of(n, cbConfig));
        Retry retry = retries.computeIfAbsent(name, n -> Retry.of(n, retryConfig));
        try {
            return CircuitBreaker.decorateSupplier(cb, Retry.decorateSupplier(retry, supplier)).get();
        } catch (Exception e) {
            if (fallback != null) {
                return fallback.get();
            }
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }
}
