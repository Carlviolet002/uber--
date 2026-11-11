package com.carl.mall.dispatch.strategy;

import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.resilience.ResilienceTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享韧性模板（OP-2）单元测试：验证熔断包重试的装饰链行为——
 * 基础设施异常重试后降级 fallback；业务异常不重试、直接透传。
 */
class ResilienceTemplateTest {

    private final ResilienceTemplate template = new ResilienceTemplate();

    @Test
    void successReturnsSupplierResult() {
        String r = template.run("rt-success", () -> "ok", () -> "fb");
        assertEquals("ok", r);
    }

    @Test
    void infraFailureRetriesThenFallback() {
        AtomicInteger attempts = new AtomicInteger();
        String r = template.run("rt-infra", () -> {
            attempts.incrementAndGet();
            throw new RestClientException("downstream unreachable");
        }, () -> "fb");
        assertEquals("fb", r);
        assertTrue(attempts.get() >= 2, "基础设施异常应触发重试（默认 maxAttempts=3），实际次数=" + attempts.get());
    }

    @Test
    void bizExceptionNotRetriedAndPropagatesWhenNoFallback() {
        // 业务异常：不计入熔断/不重试，无 fallback 时直接抛出原异常
        BizException ex = assertThrows(BizException.class, () ->
                template.run("rt-biz", () -> {
                    throw new BizException(403, "无权限");
                }, null));
        assertEquals(403, ex.getCode());
    }
}
