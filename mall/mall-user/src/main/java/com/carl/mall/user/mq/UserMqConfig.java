package com.carl.mall.user.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * real 模式 MQ 声明（D6-C）：q.user.message 队列绑定 x.order 交换机 order.#。
 * 与 mall-order 的 x.order 声明一致（幂等，重复声明无副作用），
 * 订阅全部订单域事件（order.created / order.paid / order.urged / order.status.changed）。
 */
@Configuration
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class UserMqConfig {

    @Bean
    public TopicExchange userOrderExchange() {
        return new TopicExchange("x.order");
    }

    @Bean
    public Queue userMessageQueue() {
        return new Queue("q.user.message");
    }

    @Bean
    public Binding userMessageBinding() {
        return BindingBuilder.bind(userMessageQueue())
                .to(userOrderExchange())
                .with("order.#");
    }
}
