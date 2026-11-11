package com.carl.mall.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 收货地址请求
 */
@Data
public class AddressDTO {

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "收货人手机号不能为空")
    private String receiverPhone;

    @NotNull(message = "经度不能为空")
    private Double lng;

    @NotNull(message = "纬度不能为空")
    private Double lat;

    @NotBlank(message = "详细地址不能为空")
    private String detail;

    /** 1 默认，0 非默认；空则按非默认处理 */
    private Integer isDefault;
}
