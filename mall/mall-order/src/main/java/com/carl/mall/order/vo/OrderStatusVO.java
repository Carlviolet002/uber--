package com.carl.mall.order.vo;

import lombok.Data;

/**
 * 订单状态视图（轻量查询）
 */
@Data
public class OrderStatusVO {

    private String orderNo;

    private Integer status;

    private String statusName;
}
