package com.carl.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流转日志
 */
@Data
@TableName("t_order_status_log")
public class OrderStatusLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 原状态（创建订单时为 null） */
    private Integer fromStatus;

    private Integer toStatus;

    /** 操作者类型：USER / SHOP / RIDER / SYSTEM */
    private String operatorType;

    private Long operatorId;

    private LocalDateTime time;
}
