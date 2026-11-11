package com.carl.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单评价表
 */
@Data
@TableName("t_review")
public class Review {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long shopId;

    private Long riderId;

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

    private String shopReply;

    private LocalDateTime replyTime;

    private LocalDateTime createTime;

    /** 审核状态：1 通过 / 2 下架（平台审核违规评价，默认通过） */
    private Integer auditStatus;

    private LocalDateTime auditTime;

    /** 平台回复内容（区别于商家 shop_reply） */
    private String platformReply;

    /** 回复来源：SHOP / PLATFORM */
    private String replyBy;
}
