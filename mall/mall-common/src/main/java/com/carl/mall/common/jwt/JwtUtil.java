package com.carl.mall.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.carl.mall.common.constant.RoleBitmask;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 工具（jjwt 0.12.x 新 API：Jwts.parser().verifyWith()）
 */
public final class JwtUtil {

    private JwtUtil() {
    }

    public static SecretKey buildKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static String create(Long userId, String role, String secret, long ttlMinutes) {
        SecretKey key = buildKey(secret);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(ttlMinutes))))
                .signWith(key)
                .compact();
    }

    /**
     * 新版签发：同时写入位掩码 {@code roles} 与 legacy 单值 {@code role}（双写，向下兼容）。
     *
     * @param roles        角色位掩码（USER=1/SHOP=2/RIDER=4/ADMIN=8）
     * @param legacyRole   单值角色（取位掩码主角色），用于旧客户端
     */
    public static String create(Long userId, long roles, String legacyRole, String secret, long ttlMinutes) {
        SecretKey key = buildKey(secret);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("roles", roles)
                .claim("role", legacyRole)
                .expiration(Date.from(Instant.now().plus(Duration.ofMinutes(ttlMinutes))))
                .signWith(key)
                .compact();
    }

    public static Claims parse(String token, String secret) {
        SecretKey key = buildKey(secret);
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public static Long userId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    public static String role(Claims claims) {
        return claims.get("role", String.class);
    }

    /**
     * 读取角色位掩码。兼容历史 token 未写入 {@code roles} 的情况：
     * 若 {@code roles} 缺省，则回退用 legacy {@code role} 单值换算。
     * 用 {@code Number} 接收，避免 Integer/Long 类型不一致导致的 ClassCastException。
     */
    public static long roles(Claims claims) {
        Object v = claims.get("roles");
        if (v instanceof Number n) {
            return n.longValue();
        }
        String legacy = claims.get("role", String.class);
        return legacy == null ? RoleBitmask.USER : RoleBitmask.of(legacy);
    }
}
