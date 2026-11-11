package com.carl.mall.common.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 登录结果
 */
@Data
@Builder
public class LoginResult {

    private String token;
    private Long userId;
    private String nickname;
    /** legacy 单值角色（过渡期保留，值取位掩码的主角色） */
    private String role;
    /** 角色位掩码（USER=1/SHOP=2/RIDER=4/ADMIN=8，可多值） */
    private Long roles;
    /** 角色名列表，便于前端展示 */
    private List<String> roleNames;
}
