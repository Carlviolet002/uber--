package com.carl.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carl.mall.common.constant.OrderStatus;
import com.carl.mall.order.entity.Order;
import com.carl.mall.order.entity.OrderStatusLog;
import com.carl.mall.order.mapper.OrderMapper;
import com.carl.mall.order.mapper.OrderStatusLogMapper;
import com.carl.mall.order.ws.OrderWsHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单超时处理：待支付超时关单（D6-B 改造后本类只留幂等关单方法 + 送达自动确认扫描）。
 *
 * <p>待支付超时的触发方式由开关决定（两模式行为一致，其一兜底）：
 * - mall.order.mq-timeout=true（real）：TimeoutCloseConsumer 消费延迟队列消息调 {@link #closeIfPendingPay}
 * - mall.order.mq-timeout=false（mock/回退）：PayTimeoutScanner 每 30s 扫描调 {@link #closeIfPendingPay}</p>
 */
@Slf4j
@Component
public class OrderTimeoutService {

    private final OrderMapper orderMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;

    @Value("${mall.order.pay-timeout-minutes:15}")
    private int payTimeoutMinutes;

    /** 已送达待确认超时自动确认时长（小时） */
    @Value("${mall.order.confirm-timeout-hours:2}")
    private int confirmTimeoutHours;

    public OrderTimeoutService(OrderMapper orderMapper, OrderStatusLogMapper orderStatusLogMapper) {
        this.orderMapper = orderMapper;
        this.orderStatusLogMapper = orderStatusLogMapper;
    }

    /**
     * 幂等关单：仅 status=PENDING_PAY 才取消（1 -> 7）。
     * 延迟队列消息到达时若订单已支付/已取消则不做任何事。返回是否实际关单。
     */
    public boolean closeIfPendingPay(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null || order.getStatus() == null || order.getStatus() != OrderStatus.PENDING_PAY) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelAt(now);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(order.getId());
        statusLog.setFromStatus(OrderStatus.PENDING_PAY);
        statusLog.setToStatus(OrderStatus.CANCELLED);
        statusLog.setOperatorType("SYSTEM");
        statusLog.setTime(now);
        orderStatusLogMapper.insert(statusLog);

        log.info("超时关单: {}", order.getOrderNo());
        pushStatus(order, now);
        return true;
    }

    /**
     * 查询已超时的待支付订单（供 PayTimeoutScanner 扫描）
     */
    public List<Order> findTimeoutPendingPayOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(payTimeoutMinutes);
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getStatus, OrderStatus.PENDING_PAY)
                .le(Order::getCreatedAt, deadline);
        return orderMapper.selectList(qw);
    }

    /**
     * 每 30 秒扫描一次：status=11 且送达时间超过 confirm-timeout-hours 的订单自动确认收货（11 -> 6）
     */
    @Scheduled(fixedDelay = 30000)
    public void autoConfirmDeliveredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(confirmTimeoutHours);
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getStatus, OrderStatus.DELIVERED_CONFIRM)
                .le(Order::getDeliveredTime, deadline);
        List<Order> orders = orderMapper.selectList(qw);
        for (Order order : orders) {
            LocalDateTime now = LocalDateTime.now();
            order.setStatus(OrderStatus.COMPLETED);
            order.setConfirmTime(now);
            order.setUpdateTime(now);
            orderMapper.updateById(order);

            OrderStatusLog statusLog = new OrderStatusLog();
            statusLog.setOrderId(order.getId());
            statusLog.setFromStatus(OrderStatus.DELIVERED_CONFIRM);
            statusLog.setToStatus(OrderStatus.COMPLETED);
            statusLog.setOperatorType("SYSTEM");
            statusLog.setTime(now);
            orderStatusLogMapper.insert(statusLog);

            log.info("超时自动确认收货: {}", order.getOrderNo());
            pushStatus(order, now);
        }
    }

    private void pushStatus(Order order, LocalDateTime now) {
        Map<String, Object> payload = Map.of(
                "type", "ORDER_STATUS",
                "orderNo", order.getOrderNo(),
                "status", order.getStatus(),
                "statusName", OrderStatus.name(order.getStatus()),
                "time", now.toString()
        );
        OrderWsHandler.pushToUser(order.getUserId(), payload);
    }
}
