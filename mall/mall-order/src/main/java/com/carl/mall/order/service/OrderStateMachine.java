package com.carl.mall.order.service;

import com.carl.mall.common.constant.OrderStatus;
import com.carl.mall.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态机（核心）：
 * 1 待支付        -> {2 待接单, 7 已取消}
 * 2 待接单        -> {3 派单中(外卖), 9 备餐中(自提), 8 已退款, 6 已完成(提前确认)}
 * 3 派单中        -> {9 备餐中, 4 待取餐, 8 已退款, 6 已完成(提前确认)}
 * 9 备餐中        -> {4 待取餐(外卖), 11 已送达待确认(自提出餐), 8 已退款, 6 已完成(提前确认)}
 * 4 待取餐        -> {5 配送中, 8 已退款, 6 已完成(提前确认)}
 * 5 配送中        -> {11 已送达待确认, 8 已退款, 6 已完成(提前确认)}
 * 11 已送达待确认 -> {6 已完成, 8 已退款}
 *
 * <p>自提单复用现有状态走 2→9→11→6，不进入 3 派单中，故不会被派发骑手；
 * 「随时确认收货」允许已支付后的任意非终态直达 6，统一由本状态机约束。</p>
 */
@Component
public class OrderStateMachine {

    public static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            OrderStatus.PENDING_PAY, Set.of(OrderStatus.PENDING_ACCEPT, OrderStatus.CANCELLED),
            OrderStatus.PENDING_ACCEPT, Set.of(OrderStatus.DISPATCHING, OrderStatus.COOKING,
                    OrderStatus.REFUNDED, OrderStatus.COMPLETED),
            OrderStatus.DISPATCHING, Set.of(OrderStatus.COOKING, OrderStatus.PENDING_PICKUP,
                    OrderStatus.REFUNDED, OrderStatus.COMPLETED),
            OrderStatus.COOKING, Set.of(OrderStatus.PENDING_PICKUP, OrderStatus.DELIVERED_CONFIRM,
                    OrderStatus.REFUNDED, OrderStatus.COMPLETED),
            OrderStatus.PENDING_PICKUP, Set.of(OrderStatus.DELIVERING,
                    OrderStatus.REFUNDED, OrderStatus.COMPLETED),
            OrderStatus.DELIVERING, Set.of(OrderStatus.DELIVERED_CONFIRM,
                    OrderStatus.REFUNDED, OrderStatus.COMPLETED),
            OrderStatus.DELIVERED_CONFIRM, Set.of(OrderStatus.COMPLETED, OrderStatus.REFUNDED)
    );

    /**
     * 校验状态流转合法性，不合法抛 BizException
     */
    public void assertCanTransit(int from, int to) {
        Set<Integer> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BizException("非法状态流转: " + from + " -> " + to);
        }
    }
}
