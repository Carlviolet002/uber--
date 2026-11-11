package com.carl.mall.common.dto;

import lombok.Data;

/**
 * 骑手状态快照（派单策略输入）
 */
@Data
public class RiderState {

    private Long riderId;
    private String name;
    private double lng;
    private double lat;
    /** 同时持单上限 */
    private int capacity;
    /** 当前在途单量 */
    private int currentLoad;
    /** 历史接单数 */
    private int acceptCount;
}
