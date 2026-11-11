package com.carl.mall.shop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商家回复评价请求
 */
@Data
public class ReplyDTO {

    /** 回复内容 */
    @NotBlank(message = "回复内容不能为空")
    private String content;
}
