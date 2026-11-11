package com.carl.mall.order.service;

import com.carl.mall.order.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 待支付超时扫描器（D6-B 回退路径）：
 * mall.order.mq-timeout=false（含未配置，mock 默认如此）时生效，
 * 每 30 秒扫描一次超时未支付订单并调用 OrderTimeoutService 幂等关单。
 *
 * <p>与改造前 OrderTimeoutService.closeTimeoutOrders 行为完全一致，
 * 保证 mock 模式行为不变、real 去掉开关即回退。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mall.order.mq-timeout", havingValue = "false", matchIfMissing = true)
public class PayTimeoutScanner {

    private final OrderTimeoutService orderTimeoutService;

    public PayTimeoutScanner(OrderTimeoutService orderTimeoutService) {
        this.orderTimeoutService = orderTimeoutService;
    }

    /**
     * 每 30 秒扫描一次：status=1 且创建时间超过 pay-timeout-minutes 的订单自动取消
     */
    @Scheduled(fixedDelay = 30000)
    public void closeTimeoutOrders() {
        List<Order> orders = orderTimeoutService.findTimeoutPendingPayOrders();
        for (Order order : orders) {
            orderTimeoutService.closeIfPendingPay(order.getOrderNo());
        }
    }
}
