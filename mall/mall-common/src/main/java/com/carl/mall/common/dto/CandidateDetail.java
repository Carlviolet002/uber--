package com.carl.mall.common.dto;

import lombok.Data;

/**
 * 候选骑手得分明细（落入 t_dispatch_record.candidate_details，消融分析用）
 */
@Data
public class CandidateDetail {

    private Long riderId;
    private double distance;
    private int load;
    private double score;
    private boolean selected;
}
