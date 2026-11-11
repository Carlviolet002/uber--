package com.carl.mall.shop.service;

import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.discovery.ServiceEndpointResolver;
import com.carl.mall.common.result.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单服务代理（RestClient 直连，所有远程调用转 BizException）
 */
@Service
public class ShopOrderProxyService {

    private final SimpleClientHttpRequestFactory factory;
    private final String orderUrl;
    private final ServiceEndpointResolver resolver;
    private final ObjectMapper objectMapper;

    public ShopOrderProxyService(@Value("${mall.services.order-url:http://localhost:8083}") String orderUrl,
                                 ServiceEndpointResolver resolver,
                                 ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(1000);
        f.setReadTimeout(2000);
        this.factory = f;
        this.orderUrl = orderUrl;
        this.resolver = resolver;
        this.objectMapper = objectMapper;
    }

    /** OP-1：每次调用按服务名从 Nacos 解析实例，解析失败回退直连 orderUrl。 */
    private RestClient rc() {
        return RestClient.builder().baseUrl(resolver.resolve("mall-order", orderUrl)).requestFactory(factory).build();
    }

    /**
     * 商家接单
     */
    public void accept(String orderNo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("operatorId", null);
        try {
            rc().put()
                    .uri("/internal/order/{orderNo}/shop-accept", orderNo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Object>>() {
                    });
        } catch (RestClientException e) {
            throw new BizException("订单服务不可用：" + e.getMessage());
        }
    }

    /**
     * 商家拒单
     */
    public void reject(String orderNo, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        try {
            rc().put()
                    .uri("/internal/order/{orderNo}/shop-reject", orderNo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Object>>() {
                    });
        } catch (RestClientException e) {
            throw new BizException("订单服务不可用：" + e.getMessage());
        }
    }

    /**
     * 商家出餐完成（透传订单服务，归属校验由订单服务处理）
     */
    public void finishCooking(String orderNo) {
        try {
            rc().put()
                    .uri("/internal/order/{orderNo}/finish-cooking", orderNo)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Object>>() {
                    });
        } catch (RestClientException e) {
            throw new BizException("订单服务不可用：" + e.getMessage());
        }
    }

    /**
     * 商家订单列表（代理到订单服务，data 原样返回）
     */
    public Result<Object> ordersByShop(Long shopId, Integer status, int page, int pageSize) {
        try {
            Result<Object> res = rc().get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/order/by-shop")
                            .queryParam("shopId", shopId)
                            .queryParamIfPresent("status", Optional.ofNullable(status))
                            .queryParam("page", page)
                            .queryParam("pageSize", pageSize)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Object>>() {
                    });
            if (res == null) {
                throw new BizException("订单服务返回为空");
            }
            return Result.ok(res.getData());
        } catch (RestClientException e) {
            throw new BizException("订单服务不可用：" + e.getMessage());
        }
    }

    /**
     * 商家评价列表（兼容订单服务返回 Result 或裸 list：是 Result 取 data，否则整体包一层）
     */
    public Result<Object> reviewsByShop(Long shopId) {
        String body;
        try {
            body = rc().get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/order/reviews")
                            .queryParam("shopId", shopId)
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new BizException("订单服务不可用：" + e.getMessage());
        }
        if (body == null || body.isBlank()) {
            return Result.ok(List.of());
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode data = (node.isObject() && node.has("data")) ? node.get("data") : node;
            return Result.ok(objectMapper.convertValue(data, Object.class));
        } catch (JsonProcessingException e) {
            throw new BizException("订单服务返回格式异常：" + e.getMessage());
        }
    }

    /**
     * 商家回复评价（body 传回复文本）
     */
    public void replyReview(Long reviewId, Long shopId, String content) {
        try {
            rc().put()
                    .uri(uriBuilder -> uriBuilder.path("/internal/order/review/{id}/reply")
                            .queryParam("shopId", shopId)
                            .build(reviewId))
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(content)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Object>>() {
                    });
        } catch (RestClientException e) {
            throw new BizException("订单服务不可用：" + e.getMessage());
        }
    }
}
