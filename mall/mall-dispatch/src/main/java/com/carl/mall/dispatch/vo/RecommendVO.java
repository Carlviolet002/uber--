package com.carl.mall.dispatch.vo;

import lombok.Data;

import java.util.List;

/**
 * 骑手推荐单：已指派在途订单 + 抢单池顺路候选 Top5
 */
@Data
public class RecommendVO {

    /** 已指派给该骑手的在途订单（状态 9/4/5/11，由订单服务 by-rider 按契约返回，原样展示） */
    private List<Object> myTasks;

    /** 抢单池中按顺路指数降序的 Top5 候选 */
    private List<Candidate> candidates;

    @Data
    public static class Candidate {
        private String orderNo;
        private Long shopId;
        /** 骑手当前位置到商家距离（米） */
        private double distanceMeters;
        /** 顺路指数 = 1 / (1 + 到店距离米) */
        private double score;
    }
}
