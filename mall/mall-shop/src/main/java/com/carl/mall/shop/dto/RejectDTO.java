package com.carl.mall.shop.dto;

import lombok.Data;

/**
 * 商家拒单请求
 */
@Data
public class RejectDTO {

    /** 拒单原因，可空 */
    private String reason;
}
