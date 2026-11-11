package com.carl.mall.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 骑手（运营数据）
 * <p>骑手登录账号在 user 服务 t_user（如 rider1 id=4），本表存配送运营数据，
 * 两表通过 id 关联：t_rider.id == t_user.id。</p>
 */
@Data
@TableName("t_rider")
public class Rider {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String phone;

    /** 状态：0 休息 / 1 在线 / 2 配送中 */
    private Integer status;

    /** 当前位置经度 */
    private Double currentLng;

    /** 当前位置纬度 */
    private Double currentLat;

    /** 同时持单上限 */
    private Integer capacity;

    /** 历史累计接单数（公平性指标） */
    private Integer acceptCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0 正常 / 1 已删 */
    @TableLogic
    private Integer deleted;
}
