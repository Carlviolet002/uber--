package com.carl.mall.common.event;

/**
 * 事件发布抽象：mock 模式仅打日志；real 模式发 RabbitMQ。
 * routingKey 语义：routing key（如 order.created / order.paid / rider.assigned / order.timeout）
 */
public interface EventPublisher {

    void publish(String routingKey, Object payload);

    /**
     * 延迟发布（D6-B）：real 模式经 x-delayed-message 交换机延迟投递；mock 模式仅打日志。
     *
     * @param routingKey   路由键
     * @param payload      载荷
     * @param delayMinutes 延迟分钟数
     */
    default void publishDelayed(String routingKey, Object payload, long delayMinutes) {
        // 默认实现退化为立即发布，避免旧实现类编译/行为破坏
        publish(routingKey, payload);
    }
}
