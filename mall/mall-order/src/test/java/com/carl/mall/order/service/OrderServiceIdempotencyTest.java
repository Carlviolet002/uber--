package com.carl.mall.order.service;

import com.carl.mall.common.cache.CacheService;
import com.carl.mall.common.constant.OrderStatus;
import com.carl.mall.common.discovery.ServiceEndpointResolver;
import com.carl.mall.common.event.EventPublisher;
import com.carl.mall.common.resilience.ResilienceTemplate;
import com.carl.mall.order.dto.CreateOrderDTO;
import com.carl.mall.order.entity.Order;
import com.carl.mall.order.entity.OrderDetail;
import com.carl.mall.order.mapper.OrderDetailMapper;
import com.carl.mall.order.mapper.OrderMapper;
import com.carl.mall.order.mapper.OrderStatusLogMapper;
import com.carl.mall.order.mapper.PaymentMockMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * OP-8 幂等（uniq_token 唯一约束兜底）+ OP-3（Outbox 与订单同事务）单元测试。
 *
 * <p>纯 Mockito，不起 Spring 上下文 / H2 / MQ：直接 new OrderService 并桩掉全部依赖，
 * 用 ReflectionTestUtils 注入 @Value 私有字段，聚焦 persistOrder 的
 * 「首次落库 / 重复 token 命中 / 重复但查无此单」三条路径。</p>
 */
class OrderServiceIdempotencyTest {

    private OrderMapper orderMapper;
    private OrderDetailMapper orderDetailMapper;
    private OrderStatusLogMapper orderStatusLogMapper;
    private CacheService cacheService;
    private OutboxService outboxService;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        orderDetailMapper = mock(OrderDetailMapper.class);
        orderStatusLogMapper = mock(OrderStatusLogMapper.class);
        cacheService = mock(CacheService.class);
        outboxService = mock(OutboxService.class);

        service = new OrderService(
                orderMapper,
                orderDetailMapper,
                orderStatusLogMapper,
                mock(PaymentMockMapper.class),
                mock(OrderStateMachine.class),
                cacheService,
                mock(EventPublisher.class),
                new ObjectMapper(),
                mock(ResilienceTemplate.class),
                mock(ServiceEndpointResolver.class),
                outboxService);
        ReflectionTestUtils.setField(service, "deliveryFee", new BigDecimal("5.00"));
        ReflectionTestUtils.setField(service, "payTimeoutMinutes", 15);
        ReflectionTestUtils.setField(service, "mqTimeoutEnabled", false);
    }

    @Test
    void persistOrderShouldWriteUniqTokenAndEnqueueOutboxOnFirstInsert() {
        String key = "idempotent:KEY-3";
        // 模拟 MyBatis-Plus 自增主键回填，否则 order.getId() 拆箱会 NPE
        doAnswer(inv -> {
            ((Order) inv.getArgument(0)).setId(1001L);
            return 1;
        }).when(orderMapper).insert(any(Order.class));
        OrderDetail detail = new OrderDetail();
        detail.setDishId(7L);
        List<OrderDetail> details = List.of(detail);

        Order result = service.persistOrder(1L, dto(), 120.0, 30.0, 121.0, 31.0,
                new BigDecimal("50.00"), new BigDecimal("50.00"), details, LocalDateTime.now(), key);

        ArgumentCaptor<Order> cap = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(cap.capture());
        assertEquals(key, cap.getValue().getUniqToken(), "幂等令牌应随订单落库（唯一约束的键）");
        assertEquals(OrderStatus.PENDING_PAY, cap.getValue().getStatus());
        assertNotNull(result.getOrderNo());
        assertEquals(18, result.getOrderNo().length(), "订单号=14位时间戳+4位随机数");
        assertEquals(1001L, result.getId());
        verify(orderDetailMapper).insertBatch(details);
        verify(orderStatusLogMapper).insert(any());
        verify(cacheService).set(eq(key), eq(result.getOrderNo()), eq(3600L));
        verify(outboxService).enqueue(eq("order.created"), any(), eq(0L));
        verify(outboxService, never()).enqueue(eq("order.timeout"), any(), anyLong());
    }

    @Test
    void persistOrderShouldReturnExistingOrderOnDuplicateTokenAndSkipAllWrites() {
        String key = "idempotent:KEY-1";
        Order existing = new Order();
        existing.setId(9001L);
        existing.setOrderNo("NO-EXISTING");
        existing.setUniqToken(key);
        when(orderMapper.insert(any(Order.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_uniq_token'"));
        when(orderMapper.selectOne(any())).thenReturn(existing);

        Order result = service.persistOrder(1L, dto(), 120.0, 30.0, 121.0, 31.0,
                new BigDecimal("50.00"), new BigDecimal("50.00"), List.of(), LocalDateTime.now(), key);

        assertSame(existing, result, "重复提交应返回首单，而非新建订单");
        assertEquals("NO-EXISTING", result.getOrderNo());
        verify(orderDetailMapper, never()).insertBatch(any());
        verify(orderStatusLogMapper, never()).insert(any());
        verify(cacheService, never()).set(anyString(), anyString(), anyLong());
        verify(outboxService, never()).enqueue(anyString(), any(), anyLong());
    }

    @Test
    void persistOrderShouldRethrowWhenDuplicateButNoExistingRowFound() {
        String key = "idempotent:KEY-2";
        when(orderMapper.insert(any(Order.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_uniq_token'"));
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThrows(DuplicateKeyException.class, () ->
                service.persistOrder(1L, dto(), 120.0, 30.0, 121.0, 31.0,
                        new BigDecimal("50.00"), new BigDecimal("50.00"), List.of(), LocalDateTime.now(), key));
    }

    @Test
    void persistOrderShouldNotEnqueueOrderCreatedForPickup() {
        doAnswer(inv -> {
            ((Order) inv.getArgument(0)).setId(1002L);
            return 1;
        }).when(orderMapper).insert(any(Order.class));
        CreateOrderDTO pickup = dto();
        pickup.setDeliveryType("PICKUP");

        Order result = service.persistOrder(1L, pickup, 113.66, 34.76, 113.66, 34.76,
                new BigDecimal("45.00"), new BigDecimal("45.00"), List.of(), LocalDateTime.now(),
                "idempotent:KEY-PICKUP");

        ArgumentCaptor<Order> cap = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(cap.capture());
        assertEquals("PICKUP", cap.getValue().getDeliveryType());
        assertEquals(0, BigDecimal.ZERO.compareTo(cap.getValue().getDeliveryFee()), "自提应免配送费");
        // 源头隔离：自提单不登记 order.created，派单池永远看不到它
        verify(outboxService, never()).enqueue(eq("order.created"), any(), anyLong());
        assertNotNull(result.getOrderNo());
    }

    private static CreateOrderDTO dto() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setShopId(10L);
        dto.setAddressId(20L);
        dto.setIdempotentKey("KEY");
        dto.setRemark("不要辣");
        dto.setTableware(1);
        dto.setExpectTime("12:30");
        return dto;
    }
}
