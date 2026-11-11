package com.carl.mall.shop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜品新增/修改请求
 */
@Data
public class DishDTO {

    @NotNull(message = "商家 id 不能为空")
    private Long shopId;

    @NotNull(message = "分类 id 不能为空")
    private Long categoryId;

    @NotBlank(message = "菜品名称不能为空")
    private String name;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    private BigDecimal price;

    /** 可空 */
    private String image;

    /** 1 起售，0 停售；空则默认起售 */
    private Integer status;
}
