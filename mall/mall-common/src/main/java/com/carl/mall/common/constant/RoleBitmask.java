package com.carl.mall.common.constant;

import java.util.ArrayList;
import java.util.List;

/**
 * 角色位掩码工具。
 * <p>
 * 一位用户可同时拥有多个角色，用 long 的二进制位表示：
 * USER=1、SHOP=2、RIDER=4、ADMIN=8。
 * 过渡期 JWT 同时写入位掩码 {@code roles} 与 legacy 单值 {@code role}，
 * 网关双写 {@code X-User-Roles} 与 {@code X-User-Role}，实现新旧客户端兼容。
 */
public final class RoleBitmask {

    public static final long USER = 1L;
    public static final long SHOP = 2L;
    public static final long RIDER = 4L;
    public static final long ADMIN = 8L;

    private RoleBitmask() {
    }

    /** 判断 mask 是否包含 target（按位与） */
    public static boolean has(long mask, long target) {
        return (mask & target) == target;
    }

    /** 由角色名列表计算位掩码（忽略非法值） */
    public static long fromNames(List<String> roles) {
        long mask = 0L;
        if (roles == null) {
            return mask;
        }
        for (String r : roles) {
            mask |= of(r);
        }
        return mask;
    }

    /** 单角色名 → 位掩码 */
    public static long of(String role) {
        if (role == null) {
            return 0L;
        }
        return switch (role.trim().toUpperCase()) {
            case "USER" -> USER;
            case "SHOP" -> SHOP;
            case "RIDER" -> RIDER;
            case "ADMIN" -> ADMIN;
            default -> 0L;
        };
    }

    /** 位掩码 → 角色名列表（按 USER→SHOP→RIDER→ADMIN 顺序） */
    public static List<String> toNames(long mask) {
        List<String> names = new ArrayList<>();
        if (has(mask, USER)) {
            names.add("USER");
        }
        if (has(mask, SHOP)) {
            names.add("SHOP");
        }
        if (has(mask, RIDER)) {
            names.add("RIDER");
        }
        if (has(mask, ADMIN)) {
            names.add("ADMIN");
        }
        return names;
    }

    /**
     * 取一个代表位掩码的单值角色（用于 legacy {@code role} 字段 / X-User-Role）。
     * 优先级 ADMIN > RIDER > SHOP > USER。
     */
    public static String primary(long mask) {
        if (has(mask, ADMIN)) {
            return "ADMIN";
        }
        if (has(mask, RIDER)) {
            return "RIDER";
        }
        if (has(mask, SHOP)) {
            return "SHOP";
        }
        return "USER";
    }
}
