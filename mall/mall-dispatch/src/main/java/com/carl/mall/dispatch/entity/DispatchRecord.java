package com.carl.mall.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 派单记录（每订单一行，candidate_details 存候选明细 JSON，用于策略消融分析）
 */
@Data
@TableName("t_dispatch_record")
public class DispatchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 批次号，形如 B{millis} */
    private String batchNo;

    /** 策略名：A/B/C */
    private String strategy;

    /** 指派骑手，未指派为 null */
    private Long riderId;

    /** 候选骑手数 */
    private Integer candidateCount;

    /** 候选明细 JSON（AssignResult 序列化） */
    private String candidateDetails;

    /** 决策耗时（毫秒） */
    private Integer decisionTimeMs;

    private LocalDateTime createTime;
}
