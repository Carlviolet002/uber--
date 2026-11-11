package com.carl.mall.common.constant;

/**
 * 订单状态常量（与 PRD 4.2 t_order.status 一致）
 */
public interface OrderStatus {

    /** 1 待支付 */
    int PENDING_PAY = 1;
    /** 2 待接单（已支付，商家未接） */
    int PENDING_ACCEPT = 2;
    /** 3 派单中 */
    int DISPATCHING = 3;
    /** 4 待取餐（已派骑手） */
    int PENDING_PICKUP = 4;
    /** 5 配送中 */
    int DELIVERING = 5;
    /** 6 已完成 */
    int COMPLETED = 6;
    /** 7 已取消 */
    int CANCELLED = 7;
    /** 8 已退款 */
    int REFUNDED = 8;
    /** 9 备餐中（已派骑手，商家备餐） */
    int COOKING = 9;
    /** 11 已送达待确认 */
    int DELIVERED_CONFIRM = 11;

    static String name(int status) {
        return switch (status) {
            case PENDING_PAY -> "待支付";
            case PENDING_ACCEPT -> "待接单";
            case DISPATCHING -> "派单中";
            case PENDING_PICKUP -> "待取餐";
            case DELIVERING -> "配送中";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
            case REFUNDED -> "已退款";
            case COOKING -> "备餐中";
            case DELIVERED_CONFIRM -> "已送达待确认";
            default -> "未知状态";
        };
    }
}
