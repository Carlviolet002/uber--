package com.carl.mall.user.vo;

import lombok.Data;

/**
 * 收货地址 VO
 */
@Data
public class AddressVO {

    private Long id;

    private Long userId;

    private String receiverName;

    private String receiverPhone;

    private Double lng;

    private Double lat;

    private String detail;

    private Integer isDefault;
}
