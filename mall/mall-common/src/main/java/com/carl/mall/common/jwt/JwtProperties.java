package com.carl.mall.common.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置（secret 放 yml，不入代码库）
 */
@Data
@Component
@ConfigurationProperties(prefix = "mall.jwt")
public class JwtProperties {

    /** 至少 32 字符 */
    private String secret = "mall-dispatch-default-jwt-secret-please-change-2026";

    /** Token 有效期（分钟），默认 12 小时 */
    private long ttlMinutes = 720;
}
