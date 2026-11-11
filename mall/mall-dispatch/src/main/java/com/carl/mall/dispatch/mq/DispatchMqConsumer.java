package com.carl.mall.dispatch.mq;

import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.dispatch.pending.MqPendingOrderSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * real 模式：消费订单创建事件（q.dispatch.assign ← x.order, routingKey=order.created），
 * 转成 OrderTask 入队等待批次调度。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class DispatchMqConsumer {

    private final MqPendingOrderSource source;
    private final ObjectMapper objectMapper;

    public DispatchMqConsumer(MqPendingOrderSource source, ObjectMapper objectMapper) {
        this.source = source;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "q.dispatch.assign")
    public void onOrderCreated(Map<String, Object> payload) {
        try {
            OrderTask task = objectMapper.convertValue(payload, OrderTask.class);
            source.offer(task);
            log.info("[MQ] 收到待派订单: {}", task.getOrderNo());
        } catch (Exception e) {
            log.error("[MQ] 订单消息解析失败: {}", payload, e);
        }
    }
}
