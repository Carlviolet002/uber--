package com.carl.mall.order.vo;

import com.carl.mall.order.entity.OrderDetail;
import com.carl.mall.order.entity.OrderStatusLog;
import lombok.Data;

import java.util.List;

/**
 * 订单详情视图
 */
@Data
public class OrderDetailVO {

    private OrderVO order;

    private List<OrderDetail> items;

    private List<OrderStatusLog> logs;
}
