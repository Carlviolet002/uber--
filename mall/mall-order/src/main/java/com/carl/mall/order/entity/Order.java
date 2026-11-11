package com.carl.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表
 */
@Data
@TableName("t_order")
public class Order {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单号（18 位：yyyyMMddHHmmss + 4 位随机） */
    private String orderNo;

    /** 幂等令牌（客户端传入的 idempotentKey），唯一约束兜底防止重复下单 */
    private String uniqToken;

    private Long userId;

    private Long shopId;

    /** 送达地址经度（快照） */
    private Double addressLng;

    /** 送达地址纬度（快照） */
    private Double addressLat;

    /** 订单状态：1 待支付 2 待接单 3 派单中 4 待取餐 5 配送中 6 已完成 7 已取消 8 已退款 9 备餐中 11 已送达待确认 */
    private Integer status;

    /** 订单总额（商品 + 配送费） */
    private BigDecimal total;

    /** 实付金额 */
    private BigDecimal payAmount;

    private BigDecimal deliveryFee;

    /** 配送方式：DELIVERY 外卖配送（需骑手派送）/ PICKUP 到店自提（不进入派单池、免配送费） */
    private String deliveryType;

    private LocalDateTime createdAt;

    private LocalDateTime payAt;

    private LocalDateTime acceptAt;

    private LocalDateTime assignAt;

    private LocalDateTime pickupAt;

    private LocalDateTime deliverAt;

    private LocalDateTime cancelAt;

    /** 出餐完成时间（商家出餐时记录） */
    private LocalDateTime cookTime;

    /** 送达时间（骑手送达，待用户确认收货） */
    private LocalDateTime deliveredTime;

    /** 用户确认收货时间 */
    private LocalDateTime confirmTime;

    /** 催单次数 */
    private Integer urgeCount;

    /** 最近一次催单时间 */
    private LocalDateTime urgeLastTime;

    /** 订单备注 */
    private String remark;

    /** 餐具份数 */
    private Integer tableware;

    /** 期望送达 / 期望自提时间（按 delivery_type 区分语义） */
    private String expectTime;

    /** 是否已评价：0 未评价 / 1 已评价 */
    private Integer reviewed;

    /** 接单骑手 */
    private Long riderId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
