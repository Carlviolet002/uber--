package com.carl.mall.order.config;

import com.carl.mall.order.ws.OrderWsHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：端点 /ws/order
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderWsHandler orderWsHandler;

    public WebSocketConfig(OrderWsHandler orderWsHandler) {
        this.orderWsHandler = orderWsHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderWsHandler, "/ws/order").setAllowedOrigins("*");
    }
}
