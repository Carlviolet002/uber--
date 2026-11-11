package com.carl.mall.user.mq;

import com.carl.mall.user.service.MessageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 订单事件 → 商家站内消息消费者（D6-C，仅 real 模式生效）。
 *
 * <p>订阅 q.user.message ← x.order:order.#，按事件类型生成 t_message 投递给店铺 owner 账号：
 * - order.created「收到新订单，等待顾客支付」
 * - order.paid「顾客已支付，请尽快接单」
 * - order.urged「顾客催单啦，请尽快处理」
 * - order.status.changed「订单状态更新：xxx」</p>
 *
 * <p>接收人解析：payload.shopId → t_shop.owner_user_id（单库 mall_db，直接 JdbcTemplate 查询）。
 * 无法解析接收人（无 shopId / 店铺无 owner）的消息只告警不中断消费。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class MessageConsumer {

    private final MessageService messageService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MessageConsumer(MessageService messageService,
                           JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper) {
        this.messageService = messageService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "q.user.message")
    public void onOrderEvent(org.springframework.amqp.core.Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(message.getBody(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            // 非对象载荷（如 order.timeout 的字符串订单号）不需要生成站内消息
            log.debug("[mq] 忽略非对象载荷 routingKey={}", routingKey);
            return;
        }

        String orderNo = str(payload.get("orderNo"));
        Long shopId = parseLong(payload.get("shopId"));
        if (shopId == null) {
            log.warn("[mq] 消息缺少 shopId，无法投递站内消息 routingKey={} payload={}", routingKey, payload);
            return;
        }
        Long receiverId = resolveOwner(shopId);
        if (receiverId == null) {
            log.warn("[mq] 店铺无 owner 账号，跳过站内消息 routingKey={} shopId={}", routingKey, shopId);
            return;
        }

        switch (routingKey) {
            case "order.created" -> messageService.insert(receiverId, "ORDER_CREATED",
                    "收到新订单", "订单 " + orderNo + " 已创建，等待顾客支付。", orderNo);
            case "order.paid" -> messageService.insert(receiverId, "ORDER_PAID",
                    "顾客已支付", "订单 " + orderNo + " 顾客已支付，请尽快接单。", orderNo);
            case "order.urged" -> messageService.insert(receiverId, "ORDER_URGED",
                    "顾客催单", "订单 " + orderNo + " 顾客催单啦，请尽快处理。", orderNo);
            case "order.status.changed" -> messageService.insert(receiverId, "ORDER_STATUS",
                    "订单状态更新", "订单 " + orderNo + " 状态更新：" + str(payload.get("statusName")) + "。", orderNo);
            default -> log.debug("[mq] 忽略订单事件 routingKey={}", routingKey);
        }
    }

    /** t_shop.owner_user_id 解析（单库 mall_db 直查） */
    private Long resolveOwner(Long shopId) {
        try {
            List<Long> owners = jdbcTemplate.query(
                    "SELECT owner_user_id FROM t_shop WHERE id = ? AND deleted = 0",
                    (rs, rowNum) -> rs.getObject(1) == null ? null : rs.getLong(1),
                    shopId);
            if (owners.isEmpty() || owners.get(0) == null) {
                return null;
            }
            return owners.get(0);
        } catch (Exception e) {
            log.warn("[mq] 查询店铺 owner 失败 shopId={} err={}", shopId, e.getMessage());
            return null;
        }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
