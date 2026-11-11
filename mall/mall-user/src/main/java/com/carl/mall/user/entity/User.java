package com.carl.mall.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String phone;

    /** USER / SHOP / RIDER / ADMIN（legacy 单值，取角色位掩码的主角色） */
    private String role;

    /** 角色位掩码：USER=1/SHOP=2/RIDER=4/ADMIN=8，可多值 */
    private Long roles;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
