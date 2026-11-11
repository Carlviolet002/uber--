package com.carl.mall.common.cache;

/**
 * 缓存抽象：mock 模式用内存实现，real 模式用 Redis 实现
 */
public interface CacheService {

    void set(String key, String value, long ttlSeconds);

    String get(String key);

    void delete(String key);
}
