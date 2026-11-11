package com.carl.mall.common.geo;

import java.util.List;

/**
 * GEO 索引抽象：
 * - mock 模式：内存实现（HashMap + 球面距离计算）
 * - real 模式：Redis GEOADD / GEORADIUS（5.0.7 兼容，禁用 GEOSEARCH）
 */
public interface GeoIndex {

    void add(String key, double lng, double lat, String member);

    void remove(String key, String member);

    /**
     * 查询 key 中距离 (lng,lat) 半径 radiusMeters 内的成员，按距离升序，最多 count 个
     */
    List<GeoMember> radius(String key, double lng, double lat, double radiusMeters, int count);
}
