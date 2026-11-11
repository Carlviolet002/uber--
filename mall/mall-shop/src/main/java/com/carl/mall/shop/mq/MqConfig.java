package com.carl.mall.shop.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * real 模式 MQ 声明（D6-A）：q.shop.accept 队列绑定 x.order:order.paid。
 * 与 mall-order 的 MqConfig 声明一致（幂等，重复声明无副作用），
 * 保证 mall-shop 先于订单服务启动时队列已存在，监听容器无需等待重试。
 */
@Configuration
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class MqConfig {

    @Bean
    public TopicExchange shopOrderExchange() {
        return new TopicExchange("x.order");
    }

    @Bean
    public Queue shopAcceptQueue() {
        return new Queue("q.shop.accept");
    }

    @Bean
    public Binding shopAcceptBinding() {
        return BindingBuilder.bind(shopAcceptQueue())
                .to(shopOrderExchange())
                .with("order.paid");
    }
}
