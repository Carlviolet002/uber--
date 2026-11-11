package com.carl.mall.common.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 事件发布（real 模式）
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
@ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
public class RabbitEventPublisher implements EventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${mall.event.exchange:x.order}")
    private String exchange;

    /** 延迟交换机（D6-B）：x-delayed-message 类型，默认 x.order.delay */
    @Value("${mall.event.delayed-exchange:x.order.delay}")
    private String delayedExchange;

    public RabbitEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        log.info("[MQ] exchange={} routingKey={} payload={}", exchange, routingKey, payload);
    }

    @Override
    public void publishDelayed(String routingKey, Object payload, long delayMinutes) {
        long delayMs = delayMinutes * 60_000L;
        rabbitTemplate.convertAndSend(delayedExchange, routingKey, payload, message -> {
            // x-delayed-message 插件约定：通过消息头 x-delay（毫秒）指定延迟时长
            message.getMessageProperties().setDelayLong(delayMs);
            return message;
        });
        log.info("[MQ] delayed exchange={} routingKey={} delayMinutes={} payload={}",
                delayedExchange, routingKey, delayMinutes, payload);
    }
}
