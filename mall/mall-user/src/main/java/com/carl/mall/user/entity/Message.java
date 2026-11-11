package com.carl.mall.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息实体（D6-C，t_message）
 */
@Data
@TableName("t_message")
public class Message {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 接收者账号 ID（商家账号 = t_shop.owner_user_id） */
    private Long receiverId;

    /** 消息类型：ORDER_CREATED / ORDER_PAID / ORDER_URGED / ORDER_STATUS */
    private String type;

    private String title;

    private String content;

    /** 关联业务单号（订单号） */
    private String bizNo;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
