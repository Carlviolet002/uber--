package com.carl.mall.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 派单结果：订单 → 骑手 指派（含候选明细）
 */
@Data
public class AssignResult {

    private String orderNo;
    /** 未指派时为 null */
    private Long riderId;
    private double score;
    private List<CandidateDetail> candidates;
    private long decisionTimeMs;

    public boolean assigned() {
        return riderId != null;
    }
}
