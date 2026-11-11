package com.carl.mall.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 菜品实体
 */
@Data
@TableName("t_dish")
public class Dish {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private Long categoryId;

    private String name;

    private BigDecimal price;

    private String image;

    /** 1 起售，0 停售 */
    private Integer status;

    /** 是否沽清：1 沽清，0 正常 */
    private Integer soldOut;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
