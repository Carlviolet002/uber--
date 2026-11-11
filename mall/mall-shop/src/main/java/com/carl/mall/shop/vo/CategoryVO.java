package com.carl.mall.shop.vo;

import lombok.Data;

/**
 * 菜品分类 VO
 */
@Data
public class CategoryVO {

    private Long id;

    private Long shopId;

    private String name;

    private Integer sort;
}
