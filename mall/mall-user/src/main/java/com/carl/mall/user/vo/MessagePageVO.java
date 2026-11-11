package com.carl.mall.user.vo;

import lombok.Data;

import java.util.List;

/**
 * 站内消息分页 VO（含未读数，前端徽标用）
 */
@Data
public class MessagePageVO {

    private List<MessageVO> records;

    private long total;

    private long unreadCount;
}
