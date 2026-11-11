package com.carl.mall.order.mq;

import com.carl.mall.order.service.OrderTimeoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 超时关单延迟队列消费者（D6-B）：q.order.timeout ← x.order.delay:order.timeout。
 * 消息到达后调用 OrderTimeoutService 幂等关单——订单仍为待支付才取消，
 * 已支付/已取消订单不受影响（延迟消息不误伤）。
 */
@Slf4j
@Component
@ConditionalOnExpression("'${mall.mock.enabled:true}' == 'false' and '${mall.order.mq-timeout:false}' == 'true'")
public class TimeoutCloseConsumer {

    private final OrderTimeoutService orderTimeoutService;

    public TimeoutCloseConsumer(OrderTimeoutService orderTimeoutService) {
        this.orderTimeoutService = orderTimeoutService;
    }

    @RabbitListener(queues = "q.order.timeout")
    public void onOrderTimeout(String orderNo) {
        boolean closed = orderTimeoutService.closeIfPendingPay(orderNo);
        log.info("[mq] order.timeout 消息到达 orderNo={} closed={}", orderNo, closed);
    }
}
