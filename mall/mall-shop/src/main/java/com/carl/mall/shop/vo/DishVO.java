package com.carl.mall.shop.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 菜品 VO
 */
@Data
public class DishVO {

    private Long id;

    private Long shopId;

    private Long categoryId;

    private String name;

    private BigDecimal price;

    private String image;

    private Integer status;

    /** 是否沽清：1 沽清，0 正常 */
    private Integer soldOut;
}
