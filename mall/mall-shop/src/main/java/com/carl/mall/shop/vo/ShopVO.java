package com.carl.mall.shop.vo;

import lombok.Data;

/**
 * 商家 VO
 */
@Data
public class ShopVO {

    private Long id;

    private String name;

    private String address;

    private Double lng;

    private Double lat;

    private String phone;

    private Integer status;

    /** 距离（米），无用户坐标时为 null */
    private Double distanceMeters;
}
