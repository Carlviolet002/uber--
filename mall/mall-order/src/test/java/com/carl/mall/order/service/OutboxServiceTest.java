package com.carl.mall.order.service;

import com.carl.mall.common.event.EventPublisher;
import com.carl.mall.order.entity.OutboxEvent;
import com.carl.mall.order.mapper.OutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OP-3 Outbox 事务消息单元测试（离线、不依赖 Spring 上下文 / H2 / MQ）。
 *
 * <p>桩掉 OutboxMapper 与 EventPublisher，ObjectMapper 用真实实例，覆盖
 * 「登记(enqueue) → 扫描投递(publishPending) → 标记已投递 / 失败重试 / 超限死信」
 * 全链路状态流转，验证事务消息「至少一次」语义的核心保障逻辑。</p>
 */
class OutboxServiceTest {

    private OutboxMapper outboxMapper;
    private EventPublisher eventPublisher;
    private OutboxService service;

    @BeforeEach
    void setUp() {
        outboxMapper = mock(OutboxMapper.class);
        eventPublisher = mock(EventPublisher.class);
        service = new OutboxService(outboxMapper, eventPublisher, new ObjectMapper());
    }

    // ==================== enqueue：事务内登记 ====================

    @Test
    void enqueueShouldInsertPendingEventWithSerializedPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", "NO20261105001");
        payload.put("amount", 8800);

        service.enqueue("order.created", payload, 0L);

        ArgumentCaptor<OutboxEvent> cap = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxMapper).insert(cap.capture());
        OutboxEvent e = cap.getValue();
        assertEquals("order.created", e.getEventType());
        assertEquals(0, e.getStatus());
        assertEquals(0, e.getRetryCount());
        assertEquals(0L, e.getDelayMinutes());
        assertNotNull(e.getCreateTime());
        assertNotNull(e.getPayload());
        assertTrue(e.getPayload().contains("NO20261105001"), "载荷应序列化为 JSON 文本");
        assertTrue(e.getPayload().contains("8800"));
    }

    @Test
    void enqueueShouldCarryDelayMinutesForTimeoutEvent() {
        service.enqueue("order.timeout", Collections.singletonMap("orderNo", "NO-T"), 30L);

        ArgumentCaptor<OutboxEvent> cap = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxMapper).insert(cap.capture());
        assertEquals("order.timeout", cap.getValue().getEventType());
        assertEquals(30L, cap.getValue().getDelayMinutes());
        assertEquals(0, cap.getValue().getStatus());
    }

    @Test
    void enqueueShouldThrowAndNeverInsertWhenPayloadNotSerializable() throws Exception {
        // 用桩 ObjectMapper 精确模拟序列化失败（checked Exception），验证：
        // enqueue 抛 IllegalStateException 以回滚外层事务，且绝不写入 outbox 表
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialize failed") {});
        OutboxService failingService = new OutboxService(outboxMapper, eventPublisher, failingMapper);

        assertThrows(IllegalStateException.class,
                () -> failingService.enqueue("order.created", Collections.singletonMap("orderNo", "X"), 0L));
        verify(outboxMapper, never()).insert(any());
    }

    // ==================== publishPending：扫描投递 ====================

    @Test
    void publishPendingShouldPublishImmediateEventsAndMarkSent() {
        OutboxEvent a = pending(1L, "order.created", "{\"orderNo\":\"A\"}", 0L, 0);
        OutboxEvent b = pending(2L, "order.paid", "{\"orderNo\":\"B\"}", 0L, 0);
        when(outboxMapper.selectList(any())).thenReturn(Arrays.asList(a, b));

        service.publishPending();

        ArgumentCaptor<Object> payloadCap = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publish(anyString(), payloadCap.capture());
        Set<String> orderNos = payloadCap.getAllValues().stream()
                .filter(p -> p instanceof Map)
                .map(p -> String.valueOf(((Map<?, ?>) p).get("orderNo")))
                .collect(Collectors.toSet());
        assertEquals(Set.of("A", "B"), orderNos, "JSON 载荷应反序列化为对象后透传");
        verify(eventPublisher, never()).publishDelayed(anyString(), any(), anyLong());

        assertEquals(1, a.getStatus());
        assertEquals(1, b.getStatus());
        assertNotNull(a.getSentTime());
        assertNotNull(b.getSentTime());
        verify(outboxMapper).updateById(a);
        verify(outboxMapper).updateById(b);
    }

    @Test
    void publishPendingShouldUseDelayedPublishWhenDelayPositive() {
        OutboxEvent e = pending(3L, "order.timeout", "{\"orderNo\":\"T\"}", 30L, 0);
        when(outboxMapper.selectList(any())).thenReturn(Collections.singletonList(e));

        service.publishPending();

        verify(eventPublisher).publishDelayed(eq("order.timeout"), any(), eq(30L));
        verify(eventPublisher, never()).publish(anyString(), any());
        assertEquals(1, e.getStatus());
        assertNotNull(e.getSentTime());
        verify(outboxMapper).updateById(e);
    }

    @Test
    void publishPendingShouldIncrementRetryAndKeepPendingOnFailure() {
        OutboxEvent e = pending(4L, "order.created", "{\"orderNo\":\"F\"}", 0L, 0);
        when(outboxMapper.selectList(any())).thenReturn(Collections.singletonList(e));
        doThrow(new RuntimeException("MQ down")).when(eventPublisher).publish(anyString(), any());

        service.publishPending();

        assertEquals(1, e.getRetryCount());
        assertEquals(0, e.getStatus(), "未达上限前应仍为待投递");
        assertNull(e.getSentTime());
        verify(outboxMapper).updateById(e);
    }

    @Test
    void publishPendingShouldMarkDeadLetterAfterMaxRetry() {
        OutboxEvent e = pending(5L, "order.created", "{\"orderNo\":\"D\"}", 0L, 9);
        when(outboxMapper.selectList(any())).thenReturn(Collections.singletonList(e));
        doThrow(new RuntimeException("MQ down")).when(eventPublisher).publish(anyString(), any());

        service.publishPending();

        assertEquals(10, e.getRetryCount());
        assertEquals(2, e.getStatus(), "重试达上限(10)应置死信");
        verify(outboxMapper).updateById(e);
    }

    @Test
    void publishPendingShouldDoNothingWhenQueueEmpty() {
        when(outboxMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.publishPending();

        verify(eventPublisher, never()).publish(anyString(), any());
        verify(eventPublisher, never()).publishDelayed(anyString(), any(), anyLong());
        verify(outboxMapper, never()).updateById(any());
    }

    @Test
    void publishPendingShouldSwallowScanException() {
        // 表未就绪 / DB 抖动：仅告警，不把异常抛给调度线程
        when(outboxMapper.selectList(any())).thenThrow(new RuntimeException("table not ready"));

        assertDoesNotThrow(() -> service.publishPending());
        verify(eventPublisher, never()).publish(anyString(), any());
    }

    private static OutboxEvent pending(long id, String type, String jsonPayload, long delay, int retry) {
        OutboxEvent e = new OutboxEvent();
        e.setId(id);
        e.setEventType(type);
        e.setPayload(jsonPayload);
        e.setDelayMinutes(delay);
        e.setStatus(0);
        e.setRetryCount(retry);
        e.setCreateTime(LocalDateTime.now());
        return e;
    }
}
