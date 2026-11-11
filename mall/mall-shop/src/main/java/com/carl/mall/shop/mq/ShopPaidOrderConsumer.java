package com.carl.mall.shop.mq;

import com.carl.mall.shop.service.PaidOrderNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付事件消费者（D6-A，仅 real 模式生效）：
 * 消费 q.shop.accept ← x.order:order.paid，把已支付订单号写入 Redis
 * `shop:paid:{shopId}` 待接单缓存，闭环「支付成功 → 商家」事件链路，队列不再堆积。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class ShopPaidOrderConsumer {

    private final PaidOrderNoticeService paidOrderNoticeService;

    @RabbitListener(queues = "q.shop.accept")
    public void onOrderPaid(Map<String, Object> message) {
        Object orderNoObj = message.get("orderNo");
        Object shopIdObj = message.get("shopId");
        if (orderNoObj == null || shopIdObj == null) {
            log.warn("[mq] order.paid 消息缺少 orderNo/shopId: {}", message);
            return;
        }
        String orderNo = String.valueOf(orderNoObj);
        Long shopId = Long.parseLong(String.valueOf(shopIdObj));
        paidOrderNoticeService.addPaidOrder(shopId, orderNo);
    }
}
