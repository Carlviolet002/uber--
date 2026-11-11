package com.carl.mall.order.dto;

import lombok.Data;

/**
 * 提交评价请求（orderNo 从路径获取）
 */
@Data
public class ReviewDTO {

    /** 商家综合评分（1~5） */
    private Integer shopScoreOverall;

    /** 口味评分（1~5） */
    private Integer shopScoreTaste;

    /** 包装评分（1~5） */
    private Integer shopScorePack;

    /** 骑手服务评分（1~5） */
    private Integer riderScoreService;

    /** 骑手配送速度评分（1~5） */
    private Integer riderScoreSpeed;

    private String content;

    private String images;

    /** 是否匿名：0 否 / 1 是 */
    private Integer anonymous;
}
