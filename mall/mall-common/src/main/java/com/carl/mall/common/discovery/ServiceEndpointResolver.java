package com.carl.mall.common.discovery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.stereotype.Component;

/**
 * 服务端点解析器（OP-1）。
 *
 * <p>默认走 Nacos：通过 Spring Cloud {@link LoadBalancerClient} 按服务名解析出一个实例
 * {@code http://ip:port}（底层由 Nacos 服务发现提供实例列表）。</p>
 *
 * <p><b>失败回退</b>：以下任一情形都回退到调用方传入的 fallback（即 {@code mall.services.*-url}
 * 硬编码地址，如 {@code http://localhost:808X}），保证 Nacos 不可用 / 无实例 / 关闭开关时仍可直连：</p>
 * <ul>
 *   <li>{@code mall.services.use-nacos=false}（显式关闭，则完全走直连）</li>
 *   <li>容器中无 {@link LoadBalancerClient}（如 mock 模式未启用发现）</li>
 *   <li>Nacos 无该服务实例（choose 返回 null）或解析抛异常</li>
 * </ul>
 *
 * <p>每次调用实时解析，实例注册/下线后下一请求即生效（无需重启）。</p>
 */
@Slf4j
@Component
public class ServiceEndpointResolver {

    private final ObjectProvider<LoadBalancerClient> loadBalancerProvider;

    /** 是否走 Nacos 服务发现（默认 true）。设为 false 则一律直连 fallback 地址。 */
    @Value("${mall.services.use-nacos:true}")
    private boolean useNacos;

    public ServiceEndpointResolver(ObjectProvider<LoadBalancerClient> loadBalancerProvider) {
        this.loadBalancerProvider = loadBalancerProvider;
    }

    /**
     * 解析服务基址。
     *
     * @param serviceName Nacos 注册名，如 mall-user / mall-shop / mall-order
     * @param fallbackUrl 解析失败时的直连地址，如 http://localhost:8081
     * @return 形如 http://ip:port 的基址（不带结尾斜杠）
     */
    public String resolve(String serviceName, String fallbackUrl) {
        if (!useNacos) {
            return fallbackUrl;
        }
        LoadBalancerClient lb = loadBalancerProvider.getIfAvailable();
        if (lb == null) {
            log.debug("[discovery] 无 LoadBalancerClient，回退 {} -> {}", serviceName, fallbackUrl);
            return fallbackUrl;
        }
        try {
            ServiceInstance si = lb.choose(serviceName);
            if (si != null && si.getHost() != null && si.getPort() > 0) {
                String base = "http://" + si.getHost() + ":" + si.getPort();
                log.debug("[discovery] Nacos 解析 {} -> {}", serviceName, base);
                return base;
            }
            log.debug("[discovery] Nacos 无实例 {}，回退 {}", serviceName, fallbackUrl);
        } catch (Exception e) {
            log.warn("[discovery] Nacos 解析 {} 失败，回退 {}：{}", serviceName, fallbackUrl, e.getMessage());
        }
        return fallbackUrl;
    }
}
