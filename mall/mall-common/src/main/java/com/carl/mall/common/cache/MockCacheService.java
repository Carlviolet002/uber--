package com.carl.mall.common.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存缓存（mock 模式，无 Redis 时可用）
 */
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockCacheService implements CacheService {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private record Entry(String value, long expireAtMillis) {
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlSeconds * 1000));
    }

    @Override
    public String get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAtMillis() < System.currentTimeMillis()) {
            store.remove(key);
            return null;
        }
        return entry.value();
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }
}
