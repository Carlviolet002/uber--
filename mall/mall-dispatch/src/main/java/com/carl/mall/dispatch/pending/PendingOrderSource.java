package com.carl.mall.dispatch.pending;

import com.carl.mall.common.dto.OrderTask;

import java.util.List;

/**
 * 待派订单来源抽象：
 * - mock 模式：HTTP 轮询订单服务
 * - real 模式：RabbitMQ 消息队列
 */
public interface PendingOrderSource {

    /** 取走当前积压的全部待派订单 */
    List<OrderTask> poll();
}
