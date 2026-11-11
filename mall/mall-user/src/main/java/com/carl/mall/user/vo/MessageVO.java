package com.carl.mall.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息 VO
 */
@Data
public class MessageVO {

    private Long id;

    private String type;

    private String title;

    private String content;

    /** 关联业务单号（订单号），前端跳转订单详情用 */
    private String bizNo;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    private LocalDateTime createTime;
}
