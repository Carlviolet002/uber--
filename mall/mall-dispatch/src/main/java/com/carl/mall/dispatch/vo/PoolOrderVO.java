package com.carl.mall.dispatch.vo;

import com.carl.mall.common.dto.OrderTask;
import lombok.Data;

/**
 * 抢单池订单条目（骑手端含到店距离；管理端 distanceMeters 省略为 null）
 */
@Data
public class PoolOrderVO {

    private Long orderId;
    private String orderNo;
    private Long shopId;
    /** 骑手当前位置到商家（取货点）的距离（米）；无骑手视角时（管理端）为 null */
    private Double distanceMeters;
    /** 下单时间（epoch millis） */
    private long createdAt;
    /** 承诺配送时长（分钟） */
    private int expectedMinutes;

    public static PoolOrderVO from(OrderTask task, Double distanceMeters) {
        PoolOrderVO vo = new PoolOrderVO();
        vo.setOrderId(task.getOrderId());
        vo.setOrderNo(task.getOrderNo());
        vo.setShopId(task.getShopId());
        vo.setDistanceMeters(distanceMeters);
        vo.setCreatedAt(task.getCreatedAt());
        vo.setExpectedMinutes(task.getExpectedMinutes());
        return vo;
    }
}
