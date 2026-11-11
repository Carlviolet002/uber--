package com.carl.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细（菜品快照）
 */
@Data
@TableName("t_order_detail")
public class OrderDetail {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long dishId;

    /** 菜品名称（下单时快照） */
    private String dishName;

    private Integer num;

    /** 下单时单价（快照） */
    private BigDecimal unitPrice;

    private LocalDateTime createTime;
}
