package com.carl.mall.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 骑手操作请求（接单/取餐/送达）
 */
@Data
public class RiderActionDTO {

    @NotNull(message = "riderId 不能为空")
    private Long riderId;
}
