package com.carl.mall.shop.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商家营业状态修改请求
 */
@Data
public class ShopStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
