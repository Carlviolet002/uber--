package com.carl.mall.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单列表/概要视图
 */
@Data
public class OrderVO {

    private Long orderId;

    private String orderNo;

    private Long shopId;

    private Integer status;

    private String statusName;

    private BigDecimal total;

    private BigDecimal payAmount;

    private BigDecimal deliveryFee;

    /** 配送方式：DELIVERY 外卖配送 / PICKUP 到店自提 */
    private String deliveryType;

    private LocalDateTime createdAt;

    private Long riderId;

    /** 出餐完成时间（商家出餐时记录） */
    private LocalDateTime cookTime;

    /** 催单次数 */
    private Integer urgeCount;

    /** 最近一次催单时间 */
    private LocalDateTime urgeLastTime;

    /** 订单备注 */
    private String remark;

    /** 餐具份数 */
    private Integer tableware;

    /** 期望送达 / 期望自提时间（按 deliveryType 区分语义） */
    private String expectTime;

    /** 是否已评价：0 未评价 / 1 已评价 */
    private Integer reviewed;

    /** 菜品明细摘要（快照） */
    private List<OrderItemVO> items;
}
