package com.carl.mall.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 派单请求（派单服务调用）
 */
@Data
public class AssignDTO {

    @NotNull(message = "riderId 不能为空")
    private Long riderId;
}
