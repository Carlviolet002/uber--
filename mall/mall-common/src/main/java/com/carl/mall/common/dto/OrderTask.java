package com.carl.mall.common.dto;

import lombok.Data;

/**
 * 待派订单任务（订单服务 → 派单服务的内部传输对象）
 */
@Data
public class OrderTask {

    private Long orderId;
    private String orderNo;
    private Long shopId;
    /** 商家（取货点）经度 */
    private double shopLng;
    /** 商家（取货点）纬度 */
    private double shopLat;
    /** 送达地址经度（快照） */
    private double addressLng;
    /** 送达地址纬度（快照） */
    private double addressLat;
    /** 承诺配送时长（分钟） */
    private int expectedMinutes;
    /** 下单时间（epoch millis） */
    private long createdAt;
}
