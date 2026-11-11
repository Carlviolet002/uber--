package com.carl.mall.order.ws;

import com.carl.mall.common.jwt.JwtProperties;
import com.carl.mall.common.jwt.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单 WebSocket 处理器：/ws/order?token=xxx
 * 按 userId 维护会话，用于订单状态变更实时推送
 */
@Slf4j
@Component
public class OrderWsHandler extends TextWebSocketHandler {

    /** userId -> 会话集合 */
    private static final ConcurrentHashMap<Long, Set<WebSocketSession>> USER_SESSIONS = new ConcurrentHashMap<>();

    /** Spring 单例 ObjectMapper（静态持有，供静态推送方法使用） */
    private static ObjectMapper MAPPER;

    private final JwtProperties jwtProperties;

    public OrderWsHandler(JwtProperties jwtProperties, ObjectMapper objectMapper) {
        this.jwtProperties = jwtProperties;
        MAPPER = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = parseUserId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        USER_SESSIONS.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("[ws] 用户 {} 已连接，会话数 {}", userId, USER_SESSIONS.get(userId).size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        for (Set<WebSocketSession> sessions : USER_SESSIONS.values()) {
            sessions.remove(session);
        }
        USER_SESSIONS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * 向指定用户的全部会话推送 JSON
     */
    public static void pushToUser(Long userId, Map<String, Object> payload) {
        if (userId == null || payload == null) {
            return;
        }
        Set<WebSocketSession> sessions = USER_SESSIONS.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[ws] 序列化失败 userId={} err={}", userId, e.getMessage());
            return;
        }
        synchronized (sessions) {
            for (WebSocketSession session : sessions) {
                if (session == null || !session.isOpen()) {
                    continue;
                }
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (Exception ignored) {
                    // 单会话发送失败忽略
                }
            }
        }
    }

    private Long parseUserId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) {
                return null;
            }
            String token = null;
            for (String pair : uri.getQuery().split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    token = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
            if (token == null || token.isBlank()) {
                return null;
            }
            Claims claims = JwtUtil.parse(token, jwtProperties.getSecret());
            return JwtUtil.userId(claims);
        } catch (Exception e) {
            log.warn("[ws] token 校验失败: {}", e.getMessage());
            return null;
        }
    }
}
