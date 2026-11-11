package com.carl.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OP-3 Outbox 事务消息：与业务写入在同一事务落库，独立投递器扫描发送，保证"至少一次"。
 */
@Data
@TableName("t_outbox")
public class OutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件类型 = MQ routingKey，如 order.created / order.timeout */
    private String eventType;

    /** 事件载荷（JSON 文本） */
    private String payload;

    /** 延迟投递分钟数（0 表示立即投递） */
    private Long delayMinutes;

    /** 状态：0=待投递 1=已投递 2=死信（重试超限） */
    private Integer status;

    /** 重试次数 */
    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime sentTime;
}
