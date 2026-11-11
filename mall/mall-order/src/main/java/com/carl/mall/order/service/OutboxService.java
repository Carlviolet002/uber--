package com.carl.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carl.mall.common.event.EventPublisher;
import com.carl.mall.order.entity.OutboxEvent;
import com.carl.mall.order.mapper.OutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OP-3 Outbox 事务消息投递器。
 *
 * <p>写入：{@link #enqueue} 由业务方在【同一事务】内调用（如 OrderService.persistOrder），
 * 与订单写入原子提交——业务成功则事件必在 outbox，业务回滚则事件一并回滚，
 * 彻底消除"事务提交后发 MQ 但进程崩溃导致事件丢失"的窗口。</p>
 *
 * <p>投递：{@link #publishPending} 由调度线程周期扫描 status=0 的事件并发送到 MQ，
 * 成功后置已投递（status=1）；失败重试，超过 {@link #MAX_RETRY} 置死信（status=2）。
 * 语义为"至少一次"，消费端需幂等（订单服务各消费方按 orderNo/状态机天然幂等）。</p>
 */
@Slf4j
@Service
public class OutboxService {

    private static final int MAX_RETRY = 10;
    private static final int BATCH_SIZE = 50;

    private final OutboxMapper outboxMapper;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxMapper outboxMapper, EventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 在同一事务内登记一条待投递事件（调用方需处于 @Transactional 中）。
     */
    public void enqueue(String eventType, Object payload, long delayMinutes) {
        OutboxEvent event = new OutboxEvent();
        event.setEventType(eventType);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            // 序列化失败属编码问题，抛出以回滚外层事务，避免订单落库却无对应事件
            throw new IllegalStateException("Outbox 事件序列化失败：" + eventType, e);
        }
        event.setDelayMinutes(delayMinutes);
        event.setStatus(0);
        event.setRetryCount(0);
        event.setCreateTime(LocalDateTime.now());
        outboxMapper.insert(event);
    }

    /**
     * 周期扫描并投递待发送事件（单实例内单调度线程，天然避免并发重复投递）。
     */
    @Scheduled(fixedDelayString = "${mall.order.outbox.interval-ms:1000}",
            initialDelayString = "${mall.order.outbox.initial-delay-ms:3000}")
    public void publishPending() {
        List<OutboxEvent> list;
        try {
            list = outboxMapper.selectList(new LambdaQueryWrapper<OutboxEvent>()
                    .eq(OutboxEvent::getStatus, 0)
                    .orderByAsc(OutboxEvent::getId)
                    .last("limit " + BATCH_SIZE));
        } catch (Exception e) {
            // 表未就绪 / DB 抖动时仅告警，不中断调度
            log.debug("[outbox] 扫描待发事件失败：{}", e.getMessage());
            return;
        }
        if (list == null || list.isEmpty()) {
            return;
        }
        for (OutboxEvent event : list) {
            try {
                Object payload = objectMapper.readValue(event.getPayload(), Object.class);
                long delay = event.getDelayMinutes() == null ? 0L : event.getDelayMinutes();
                if (delay > 0) {
                    eventPublisher.publishDelayed(event.getEventType(), payload, delay);
                } else {
                    eventPublisher.publish(event.getEventType(), payload);
                }
                event.setStatus(1);
                event.setSentTime(LocalDateTime.now());
                outboxMapper.updateById(event);
            } catch (Exception e) {
                int retry = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
                event.setRetryCount(retry);
                if (retry >= MAX_RETRY) {
                    event.setStatus(2);
                    log.error("[outbox] 投递失败超限置死信 id={} type={} retry={}", event.getId(), event.getEventType(), retry);
                } else {
                    log.warn("[outbox] 投递失败(第{}次) id={} type={}：{}", retry, event.getId(), event.getEventType(), e.getMessage());
                }
                outboxMapper.updateById(event);
            }
        }
    }
}
