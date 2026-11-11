package com.carl.mall.order.service;

import com.carl.mall.common.constant.OrderStatus;
import com.carl.mall.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 订单状态机单元测试（纯逻辑，无 Spring 上下文）。
 * 覆盖《订单状态机》10 态所有合法流转，以及典型非法流转必须抛 BizException。
 */
class OrderStateMachineTest {

    private final OrderStateMachine fsm = new OrderStateMachine();

    /** 合法流转全集（来自 OrderStateMachine.TRANSITIONS） */
    private static final Object[][] LEGAL = {
            {OrderStatus.PENDING_PAY, OrderStatus.PENDING_ACCEPT},
            {OrderStatus.PENDING_PAY, OrderStatus.CANCELLED},
            {OrderStatus.PENDING_ACCEPT, OrderStatus.DISPATCHING},
            {OrderStatus.PENDING_ACCEPT, OrderStatus.REFUNDED},
            {OrderStatus.DISPATCHING, OrderStatus.COOKING},
            {OrderStatus.DISPATCHING, OrderStatus.PENDING_PICKUP},
            {OrderStatus.DISPATCHING, OrderStatus.REFUNDED},
            {OrderStatus.COOKING, OrderStatus.PENDING_PICKUP},
            {OrderStatus.PENDING_PICKUP, OrderStatus.DELIVERING},
            {OrderStatus.PENDING_PICKUP, OrderStatus.REFUNDED},
            {OrderStatus.DELIVERING, OrderStatus.DELIVERED_CONFIRM},
            {OrderStatus.DELIVERING, OrderStatus.REFUNDED},
            {OrderStatus.DELIVERED_CONFIRM, OrderStatus.COMPLETED},
            {OrderStatus.PENDING_ACCEPT, OrderStatus.COOKING},          // 自提：商家接单直接备餐
            {OrderStatus.PENDING_ACCEPT, OrderStatus.COMPLETED},        // 提前确认收货
            {OrderStatus.DISPATCHING, OrderStatus.COMPLETED},
            {OrderStatus.COOKING, OrderStatus.DELIVERED_CONFIRM},       // 自提：出餐后备好待取
            {OrderStatus.COOKING, OrderStatus.REFUNDED},
            {OrderStatus.COOKING, OrderStatus.COMPLETED},
            {OrderStatus.PENDING_PICKUP, OrderStatus.COMPLETED},
            {OrderStatus.DELIVERING, OrderStatus.COMPLETED},
            {OrderStatus.DELIVERED_CONFIRM, OrderStatus.REFUNDED},
    };

    @Test
    void legalTransitionsShouldPass() {
        for (Object[] pair : LEGAL) {
            int from = (int) pair[0];
            int to = (int) pair[1];
            assertDoesNotThrow(() -> fsm.assertCanTransit(from, to),
                    "合法流转应放行: " + from + " -> " + to);
        }
    }

    @Test
    void illegalTransitionsShouldThrow() {
        int[][] illegal = {
                {OrderStatus.PENDING_PAY, OrderStatus.DISPATCHING},   // 未支付不能直接派单
                {OrderStatus.PENDING_ACCEPT, OrderStatus.DELIVERING}, // 跳过派单/出餐
                {OrderStatus.COOKING, OrderStatus.DELIVERING},         // 备餐中不能直接配送
                {OrderStatus.COMPLETED, OrderStatus.PENDING_PAY},      // 已完成不可逆向
                {OrderStatus.DELIVERED_CONFIRM, OrderStatus.DELIVERING},
                {OrderStatus.CANCELLED, OrderStatus.COMPLETED},
        };
        for (int[] pair : illegal) {
            int from = pair[0];
            int to = pair[1];
            BizException ex = assertThrows(BizException.class,
                    () -> fsm.assertCanTransit(from, to),
                    "非法流转应抛异常: " + from + " -> " + to);
        }
    }

    @Test
    void unknownFromStatusShouldThrow() {
        assertThrows(BizException.class, () -> fsm.assertCanTransit(99, OrderStatus.PENDING_ACCEPT));
    }

    @Test
    void transitionSetIsComplete() {
        // 校验 TRANSITIONS 覆盖文档定义的 7 个中间态起点
        Set<Integer> keys = OrderStateMachine.TRANSITIONS.keySet();
        assert keys.contains(OrderStatus.PENDING_PAY);
        assert keys.contains(OrderStatus.PENDING_ACCEPT);
        assert keys.contains(OrderStatus.DISPATCHING);
        assert keys.contains(OrderStatus.COOKING);
        assert keys.contains(OrderStatus.PENDING_PICKUP);
        assert keys.contains(OrderStatus.DELIVERING);
        assert keys.contains(OrderStatus.DELIVERED_CONFIRM);
    }

    @Test
    void pickupFlowShouldPass() {
        // 自提单：2 -> 9 -> 11 -> 6，全程不进入 3 派单中
        int[][] flow = {
                {OrderStatus.PENDING_ACCEPT, OrderStatus.COOKING},
                {OrderStatus.COOKING, OrderStatus.DELIVERED_CONFIRM},
                {OrderStatus.DELIVERED_CONFIRM, OrderStatus.COMPLETED},
        };
        for (int[] pair : flow) {
            assertDoesNotThrow(() -> fsm.assertCanTransit(pair[0], pair[1]));
        }
    }

    @Test
    void earlyConfirmFromAnyActiveStateShouldPass() {
        int[] active = {
                OrderStatus.PENDING_ACCEPT, OrderStatus.DISPATCHING, OrderStatus.COOKING,
                OrderStatus.PENDING_PICKUP, OrderStatus.DELIVERING, OrderStatus.DELIVERED_CONFIRM
        };
        for (int from : active) {
            assertDoesNotThrow(() -> fsm.assertCanTransit(from, OrderStatus.COMPLETED));
        }
        // 待支付与终态不允许确认收货
        assertThrows(BizException.class,
                () -> fsm.assertCanTransit(OrderStatus.PENDING_PAY, OrderStatus.COMPLETED));
        assertThrows(BizException.class,
                () -> fsm.assertCanTransit(OrderStatus.CANCELLED, OrderStatus.COMPLETED));
        assertThrows(BizException.class,
                () -> fsm.assertCanTransit(OrderStatus.COMPLETED, OrderStatus.COMPLETED));
    }
}
