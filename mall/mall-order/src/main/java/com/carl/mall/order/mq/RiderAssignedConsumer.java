package com.carl.mall.order.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carl.mall.common.constant.OrderStatus;
import com.carl.mall.order.entity.Order;
import com.carl.mall.order.mapper.OrderMapper;
import com.carl.mall.order.ws.OrderWsHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 骑手派单通知消费者（仅 real 模式生效）。
 * q.order.rider 队列由 dispatch 服务声明并投递 rider.assigned 消息，
 * 这里只做 WS 推送通知，不修改订单状态。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class RiderAssignedConsumer {

    private final OrderMapper orderMapper;

    public RiderAssignedConsumer(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @RabbitListener(queues = "q.order.rider")
    public void onRiderAssigned(Map<String, Object> message) {
        Object orderNoObj = message.get("orderNo");
        if (orderNoObj == null) {
            log.warn("[mq] rider.assigned 消息缺少 orderNo: {}", message);
            return;
        }
        String orderNo = String.valueOf(orderNoObj);
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            log.warn("[mq] 未找到订单: {}", orderNo);
            return;
        }
        // 优先推送给消息中的 riderId，其次订单上的 riderId，最后订单所属用户
        Object riderIdObj = message.get("riderId");
        Long targetUserId = riderIdObj != null ? Long.parseLong(String.valueOf(riderIdObj))
                : (order.getRiderId() != null ? order.getRiderId() : order.getUserId());
        Map<String, Object> payload = Map.of(
                "type", "ORDER_STATUS",
                "orderNo", order.getOrderNo(),
                "status", order.getStatus(),
                "statusName", OrderStatus.name(order.getStatus()),
                "time", LocalDateTime.now().toString()
        );
        OrderWsHandler.pushToUser(targetUserId, payload);
    }
}
