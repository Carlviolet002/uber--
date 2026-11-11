package com.carl.mall.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 声明（仅 real 模式生效）：
 * x.order 交换机，order.created -> q.dispatch.assign，order.paid -> q.shop.accept
 */
@Configuration
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class MqConfig {

    public static final String EXCHANGE_ORDER = "x.order";
    public static final String QUEUE_DISPATCH_ASSIGN = "q.dispatch.assign";
    public static final String QUEUE_SHOP_ACCEPT = "q.shop.accept";
    public static final String ROUTING_ORDER_CREATED = "order.created";
    public static final String ROUTING_ORDER_PAID = "order.paid";

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(EXCHANGE_ORDER);
    }

    @Bean
    public Queue dispatchAssignQueue() {
        return new Queue(QUEUE_DISPATCH_ASSIGN);
    }

    @Bean
    public Queue shopAcceptQueue() {
        return new Queue(QUEUE_SHOP_ACCEPT);
    }

    @Bean
    public Binding dispatchAssignBinding() {
        return BindingBuilder.bind(dispatchAssignQueue()).to(orderExchange()).with(ROUTING_ORDER_CREATED);
    }

    @Bean
    public Binding shopAcceptBinding() {
        return BindingBuilder.bind(shopAcceptQueue()).to(orderExchange()).with(ROUTING_ORDER_PAID);
    }
}
