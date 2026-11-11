package com.carl.mall.gateway.filter;

import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.jwt.JwtProperties;
import com.carl.mall.common.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 全局 JWT 认证过滤器（WebFlux）
 * 白名单路径与 /ws/**（WebSocket）直接放行，其余校验 Authorization: Bearer xxx，
 * 解析成功后向下游注入 X-User-Id、X-User-Role 请求头
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String WS_PREFIX = "/ws/";

    private final JwtProperties jwtProperties;

    /** 白名单路径（逗号分隔，来自 mall.jwt.whitelist） */
    @Value("${mall.jwt.whitelist:}")
    private String whitelist;

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        // 白名单或 WebSocket 路径直接放行
        if (isWhitelisted(path) || path.startsWith(WS_PREFIX)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            Claims claims = JwtUtil.parse(token, jwtProperties.getSecret());
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            long roles = JwtUtil.roles(claims);
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header(CommonConstant.HEADER_USER_ID, userId)
                    .header(CommonConstant.HEADER_USER_ROLE, role == null ? "" : role)
                    .header(CommonConstant.HEADER_USER_ROLES, String.valueOf(roles))
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception e) {
            log.warn("JWT 校验失败：{}", e.getMessage());
            return unauthorized(exchange);
        }
    }

    /**
     * 判断请求路径是否在白名单中
     */
    private boolean isWhitelisted(String path) {
        if (whitelist == null || whitelist.isBlank()) {
            return false;
        }
        List<String> list = Arrays.stream(whitelist.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return list.contains(path);
    }

    /**
     * 返回 401 统一 JSON（WebFlux 风格，避免依赖 spring-boot-starter-web）
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"msg\":\"未登录或Token已过期\",\"data\":null}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
