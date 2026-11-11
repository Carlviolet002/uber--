package com.carl.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carl.mall.order.entity.OutboxEvent;

/**
 * Outbox 事件表 Mapper（OP-3）
 */
public interface OutboxMapper extends BaseMapper<OutboxEvent> {
}
