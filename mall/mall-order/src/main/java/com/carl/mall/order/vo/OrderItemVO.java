package com.carl.mall.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细条目视图（菜品快照摘要，供订单列表/详情展示）
 */
@Data
public class OrderItemVO {

    private Long dishId;

    /** 菜品名称（下单时快照） */
    private String dishName;

    private Integer num;

    /** 下单时单价（快照） */
    private BigDecimal unitPrice;
}
