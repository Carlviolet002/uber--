package com.carl.mall.dispatch.pending;

import com.carl.mall.common.dto.OrderTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * real 模式待派订单来源：MQ 消费者把订单放入内存队列，调度器定时排空
 */
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class MqPendingOrderSource implements PendingOrderSource {

    private final ConcurrentLinkedQueue<OrderTask> queue = new ConcurrentLinkedQueue<>();

    /** MQ 消费者回调：新订单入队 */
    public void offer(OrderTask task) {
        queue.offer(task);
    }

    @Override
    public List<OrderTask> poll() {
        if (queue.isEmpty()) {
            return List.of();
        }
        List<OrderTask> list = new ArrayList<>();
        OrderTask task;
        while ((task = queue.poll()) != null) {
            list.add(task);
        }
        return list;
    }
}
