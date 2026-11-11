package com.carl.mall.user.dto;

import lombok.Data;

/**
 * 修改个人信息请求
 */
@Data
public class UpdateMeDTO {

    private String nickname;

    private String phone;
}
