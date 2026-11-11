package com.carl.mall.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批次统计（每批次一行，供吞吐/空驶/耗时分析）
 */
@Data
@TableName("t_batch_stat")
public class BatchStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private String strategy;

    /** 本批订单数 */
    private Integer orderCount;

    /** 本批在线骑手数 */
    private Integer riderCount;

    /** 本批空驶距离合计：Σ 各订单最近骑手→商家距离（米） */
    private Double totalEmptyDistance;

    /** 本批派单总耗时（毫秒） */
    private Integer dispatchTimeMs;

    private LocalDateTime createTime;
}
