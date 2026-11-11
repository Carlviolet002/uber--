package com.carl.mall.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家实体
 */
@Data
@TableName("t_shop")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String address;

    /** 经度 */
    private Double lng;

    /** 纬度 */
    private Double lat;

    private String phone;

    /** 1 营业中，0 休息中 */
    private Integer status;

    /** 商家账号（t_user.id，SHOP 角色）：站内消息投递映射 shop -> 账号（D6-C） */
    private Long ownerUserId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
