package com.carl.mall.shop.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 商家「已支付待接单」订单号缓存（D6-A，仅 real 模式生效）。
 *
 * <p>消费者收到 order.paid 事件后，将订单号追加到 Redis Set `shop:paid:{shopId}`（TTL 24h），
 * 供商家端待接单提醒/联调验证（Redis key 可观测）。幂等：SADD 天然去重，重复消息无副作用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class PaidOrderNoticeService {

    private static final String KEY_PREFIX = "shop:paid:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /** 记录已支付待接单订单 */
    public void addPaidOrder(Long shopId, String orderNo) {
        String key = KEY_PREFIX + shopId;
        redisTemplate.opsForSet().add(key, orderNo);
        redisTemplate.expire(key, TTL);
        log.info("[paid-order] 已记录待接单订单 shopId={} orderNo={}", shopId, orderNo);
    }

    /** 查询已支付待接单订单号列表（供接口/前端演示，R3 可选联动） */
    public List<String> pendingPaid(Long shopId) {
        Set<String> members = redisTemplate.opsForSet().members(KEY_PREFIX + shopId);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream().sorted().toList();
    }
}
