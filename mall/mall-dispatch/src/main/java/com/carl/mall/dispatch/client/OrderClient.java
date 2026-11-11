package com.carl.mall.dispatch.client;

import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.discovery.ServiceEndpointResolver;
import com.carl.mall.common.resilience.ResilienceTemplate;
import com.carl.mall.common.result.Result;
import com.carl.mall.common.result.ResultCode;
import com.carl.mall.dispatch.service.StrategyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * 订单服务客户端（RestClient 直连 mall.services.order-url，不经网关）。
 * 所有调用经共享 {@link ResilienceTemplate} 包裹：超时 + 熔断 + 退避重试；
 * 读接口降级为空集合（不影响本轮派单），写接口（指派通知）降级为记日志放行。
 */
@Slf4j
@Component
public class OrderClient {

    private final SimpleClientHttpRequestFactory factory;
    private final String orderUrl;
    private final ServiceEndpointResolver resolver;
    private final StrategyService strategyService;
    private final ResilienceTemplate resilience;

    public OrderClient(@Value("${mall.services.order-url:http://localhost:8083}") String orderUrl,
                       ServiceEndpointResolver resolver,
                       StrategyService strategyService,
                       ResilienceTemplate resilience) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(1000);
        f.setReadTimeout(2000);
        this.factory = f;
        this.orderUrl = orderUrl;
        this.resolver = resolver;
        this.strategyService = strategyService;
        this.resilience = resilience;
    }

    /** OP-1：每次调用按服务名从 Nacos 解析实例，解析失败回退直连 orderUrl。 */
    private RestClient rc() {
        return RestClient.builder()
                .baseUrl(resolver.resolve("mall-order", orderUrl))
                .requestFactory(factory)
                .build();
    }

    /** 拉取待派订单（order 服务内部接口）。afterCook 取值与配置一致（改配置即时生效）。 */
    public List<OrderTask> pending() {
        return pendingInternal(strategyService.assignAfterCook());
    }

    /** 抢单池：未指派订单列表。 */
    public List<OrderTask> pool() {
        return pendingInternal(strategyService.assignAfterCook());
    }

    private List<OrderTask> pendingInternal(boolean afterCook) {
        return resilience.run("dispatchOrder.pending", () -> {
            RestClient.RequestHeadersSpec<?> spec = afterCook
                    ? rc().get().uri(UriComponentsBuilder.fromPath("/internal/order/pending")
                            .queryParam("afterCook", true).build().toUriString())
                    : rc().get().uri("/internal/order/pending");
            Result<List<OrderTask>> result = spec.retrieve()
                    .body(new ParameterizedTypeReference<Result<List<OrderTask>>>() {
                    });
            if (result == null || result.getData() == null) {
                return List.of();
            }
            if (result.getCode() != ResultCode.SUCCESS) {
                throw new BizException(result.getMsg());
            }
            return result.getData();
        }, () -> {
            log.warn("[dispatchOrder.pending] 订单服务不可用，降级返回空列表（本轮不派单）");
            return List.of();
        });
    }

    /** 通知订单服务：订单已指派给骑手 */
    public void assign(String orderNo, Long riderId) {
        notifyOrder("assign", orderNo, "/internal/order/{orderNo}/assign", riderId);
    }

    /** 骑手确认接单 */
    public void riderAccept(String orderNo, Long riderId) {
        notifyOrder("riderAccept", orderNo, "/internal/order/{orderNo}/rider-accept", riderId);
    }

    /** 骑手已取餐 */
    public void riderPicked(String orderNo, Long riderId) {
        notifyOrder("riderPicked", orderNo, "/internal/order/{orderNo}/picked", riderId);
    }

    /** 骑手已送达 */
    public void riderDelivered(String orderNo, Long riderId) {
        notifyOrder("riderDelivered", orderNo, "/internal/order/{orderNo}/delivered", riderId);
    }

    /**
     * 查询订单状态（data 原样返回，通常为 Map）
     */
    public Object statusOf(String orderNo) {
        return resilience.run("dispatchOrder.statusOf", () -> {
            Result<Object> result = rc().get()
                    .uri("/internal/order/{orderNo}/status", orderNo).retrieve()
                    .body(new ParameterizedTypeReference<Result<Object>>() {
                    });
            return result == null ? null : result.getData();
        }, () -> {
            log.warn("[dispatchOrder.statusOf] 订单服务不可用，降级返回 null orderNo={}", orderNo);
            return null;
        });
    }

    /**
     * 查询骑手的订单列表（status 为 null 时由订单服务默认查 4/5：待取餐/配送中）
     */
    public List<Object> byRider(Long riderId, Integer status, int page, int pageSize) {
        return resilience.run("dispatchOrder.byRider", () -> {
            UriComponentsBuilder ub = UriComponentsBuilder.fromPath("/internal/order/by-rider")
                    .queryParam("riderId", riderId)
                    .queryParam("status", status)
                    .queryParam("page", page)
                    .queryParam("pageSize", pageSize);
            Result<List<Object>> result = rc().get().uri(ub.build().toUriString()).retrieve()
                    .body(new ParameterizedTypeReference<Result<List<Object>>>() {
                    });
            if (result == null || result.getData() == null) {
                return List.of();
            }
            if (result.getCode() != ResultCode.SUCCESS) {
                throw new BizException(result.getMsg());
            }
            return result.getData();
        }, () -> {
            log.warn("[dispatchOrder.byRider] 订单服务不可用，降级返回空列表 riderId={}", riderId);
            return List.of();
        });
    }

    /**
     * PUT 通知订单服务：读取返回体 Result，业务失败（code != 200）时把订单服务的错误消息透传
     * 为 BizException（抢单/指派的状态校验失败消息即由此透传）。基础设施故障经重试耗尽后降级记日志放行。
     */
    private void notifyOrder(String name, String orderNo, String uriTemplate, Long riderId) {
        resilience.run("dispatchOrder." + name, () -> {
            Result<Void> result = rc().put()
                    .uri(uriTemplate, orderNo)
                    .body(Map.of("riderId", riderId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Void>>() {
                    });
            if (result != null && result.getCode() != ResultCode.SUCCESS) {
                String msg = result.getMsg() == null || result.getMsg().isBlank()
                        ? "订单服务指派失败" : result.getMsg();
                throw new BizException(msg);
            }
            return null;
        }, () -> {
            log.warn("[dispatchOrder.{}] 通知订单服务失败(重试耗尽) orderNo={} uri={}", name, orderNo, uriTemplate);
            return null;
        });
    }
}
