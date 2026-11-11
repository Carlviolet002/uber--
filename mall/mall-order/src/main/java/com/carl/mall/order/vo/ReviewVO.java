package com.carl.mall.order.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价视图
 */
@Data
public class ReviewVO {

    private Long id;

    private String orderNo;

    private Long userId;

    /** 用户昵称（mock 模式无法查询 user 服务，置空） */
    private String userNickname;

    private Long shopId;

    private Long riderId;

    private Integer shopScoreOverall;

    private Integer shopScoreTaste;

    private Integer shopScorePack;

    private Integer riderScoreService;

    private Integer riderScoreSpeed;

    private String content;

    private String images;

    private Integer anonymous;

    private String shopReply;

    private LocalDateTime replyTime;

    private LocalDateTime createTime;

    /** 审核状态：1 通过 / 2 下架 */
    private Integer auditStatus;

    private LocalDateTime auditTime;

    /** 平台回复内容 */
    private String platformReply;

    /** 回复来源：SHOP / PLATFORM */
    private String replyBy;
}
