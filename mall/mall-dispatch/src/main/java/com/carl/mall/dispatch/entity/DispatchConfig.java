package com.carl.mall.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单配置项（当前策略等运行期可切换配置）
 */
@Data
@TableName("t_dispatch_config")
public class DispatchConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置键，如 dispatch.strategy */
    private String cfgKey;

    /** 配置值，如 A/B/C */
    private String cfgValue;

    private LocalDateTime updateTime;

    private LocalDateTime createTime;
}
