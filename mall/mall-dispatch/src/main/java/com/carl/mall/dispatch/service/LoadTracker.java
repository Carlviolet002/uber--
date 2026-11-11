package com.carl.mall.dispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 骑手在途负载跟踪器。
 *
 * <p>mock 简化为内存负载（重启即空）；生产环境应查询订单库统计骑手在途单量。</p>
 */
@Slf4j
@Component
public class LoadTracker {

    private final ConcurrentHashMap<Long, Integer> loadMap = new ConcurrentHashMap<>();

    /** 骑手接单 +1 */
    public void inc(Long riderId) {
        loadMap.merge(riderId, 1, Integer::sum);
    }

    /** 骑手完成一单 -1（不低于 0） */
    public void dec(Long riderId) {
        loadMap.computeIfPresent(riderId, (k, v) -> Math.max(0, v - 1));
    }

    /** 当前在途单量 */
    public int get(Long riderId) {
        return loadMap.getOrDefault(riderId, 0);
    }

    /** 清空（重启即空，等价于新实例内存状态） */
    public void reset() {
        loadMap.clear();
    }
}
