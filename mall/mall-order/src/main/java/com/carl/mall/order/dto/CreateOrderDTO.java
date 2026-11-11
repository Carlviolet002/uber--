package com.carl.mall.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求
 */
@Data
public class CreateOrderDTO {

    @NotNull(message = "shopId 不能为空")
    private Long shopId;

    /** 收货地址 ID：外卖配送必填；到店自提可为空（后端跳过地址校验，坐标用店铺位置兜底） */
    private Long addressId;

    @NotEmpty(message = "items 不能为空")
    @Valid
    private List<ItemDTO> items;

    @NotBlank(message = "idempotentKey 不能为空")
    private String idempotentKey;

    /** 订单备注（选填） */
    private String remark;

    /** 餐具份数（选填，默认 0） */
    private Integer tableware;

    /** 期望送达 / 期望自提时间（选填，如 "12:30" / "尽快送出" / "尽快取餐"，按 deliveryType 区分） */
    private String expectTime;

    /** 配送方式：DELIVERY 外卖配送（默认）/ PICKUP 到店自提；空值按 DELIVERY 兼容旧调用 */
    private String deliveryType;

    /**
     * 下单菜品项
     */
    @Data
    public static class ItemDTO {

        @NotNull(message = "dishId 不能为空")
        private Long dishId;

        @NotNull(message = "num 不能为空")
        @Min(value = 1, message = "num 必须大于等于 1")
        private Integer num;
    }
}
