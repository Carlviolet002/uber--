package com.carl.mall.dispatch.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * real 模式 MQ 声明：
 * - x.order：订单域主题交换机；q.dispatch.assign 绑定 order.created（订单创建事件 → 派单）
 * - x.rider：骑手域主题交换机；q.order.rider 绑定 rider.assigned（派单结果 → 订单服务）
 */
@Configuration
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class MqConfig {

    // ===== 订单域：订单创建事件 → 派单服务 =====

    @Bean
    public TopicExchange orderTopicExchange() {
        return new TopicExchange("x.order");
    }

    @Bean
    public Queue dispatchAssignQueue() {
        return new Queue("q.dispatch.assign");
    }

    @Bean
    public Binding dispatchAssignBinding() {
        return BindingBuilder.bind(dispatchAssignQueue())
                .to(orderTopicExchange())
                .with("order.created");
    }

    // ===== 骑手域：派单结果 → 订单服务 =====

    @Bean
    public TopicExchange riderTopicExchange() {
        return new TopicExchange("x.rider");
    }

    @Bean
    public Queue orderRiderQueue() {
        return new Queue("q.order.rider");
    }

    @Bean
    public Binding orderRiderBinding() {
        return BindingBuilder.bind(orderRiderQueue())
                .to(riderTopicExchange())
                .with("rider.assigned");
    }
}
