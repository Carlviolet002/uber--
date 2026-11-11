package com.carl.mall.order.dto;

import lombok.Data;

/**
 * 商家操作请求（接单/拒单）
 */
@Data
public class ShopActionDTO {

    /** 拒单原因（可空） */
    private String reason;
}
