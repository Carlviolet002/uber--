package com.carl.mall.order.dto;

import lombok.Data;

/**
 * 取消订单请求
 */
@Data
public class CancelDTO {

    /** 取消原因（可空，退款场景写入日志） */
    private String reason;
}
