package com.carl.mall.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息 VO（不含密码）
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String phone;

    private String role;

    /** 角色位掩码 */
    private Long roles;

    private LocalDateTime createTime;
}
