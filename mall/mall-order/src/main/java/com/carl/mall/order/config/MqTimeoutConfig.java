package com.carl.mall.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 超时关单延迟队列声明（D6-B）：x.order.delay（x-delayed-message）+ q.order.timeout。
 *
 * <p>生效条件：real 模式 且 mall.order.mq-timeout=true。声明与消费者同开关——
 * 插件缺失/不想用延迟队列时，去掉 real yml 中的 mall.order.mq-timeout 即整体回退到
 * 30s 扫描器（PayTimeoutScanner），本类不生效、启动不受影响，无需改代码。</p>
 */
@Configuration
@ConditionalOnExpression("'${mall.mock.enabled:true}' == 'false' and '${mall.order.mq-timeout:false}' == 'true'")
public class MqTimeoutConfig {

    public static final String EXCHANGE_ORDER_DELAY = "x.order.delay";
    public static final String QUEUE_ORDER_TIMEOUT = "q.order.timeout";
    public static final String ROUTING_ORDER_TIMEOUT = "order.timeout";

    @Bean
    public CustomExchange orderDelayExchange() {
        Map<String, Object> args = new HashMap<>();
        // x-delayed-message 插件约定：底层交换机类型
        args.put("x-delayed-type", "topic");
        return new CustomExchange(EXCHANGE_ORDER_DELAY, "x-delayed-message", true, false, args);
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return new Queue(QUEUE_ORDER_TIMEOUT);
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderDelayExchange())
                .with(ROUTING_ORDER_TIMEOUT)
                .noargs();
    }
}
