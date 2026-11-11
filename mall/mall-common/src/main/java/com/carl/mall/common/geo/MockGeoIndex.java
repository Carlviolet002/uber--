package com.carl.mall.common.geo;

import com.carl.mall.common.util.DistanceUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 GEO 索引（mock 模式）
 */
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockGeoIndex implements GeoIndex {

    private final Map<String, Map<String, double[]>> store = new ConcurrentHashMap<>();

    @Override
    public void add(String key, double lng, double lat, String member) {
        store.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(member, new double[]{lng, lat});
    }

    @Override
    public void remove(String key, String member) {
        Map<String, double[]> members = store.get(key);
        if (members != null) {
            members.remove(member);
        }
    }

    @Override
    public List<GeoMember> radius(String key, double lng, double lat, double radiusMeters, int count) {
        Map<String, double[]> members = store.get(key);
        List<GeoMember> result = new ArrayList<>();
        if (members == null) {
            return result;
        }
        for (Map.Entry<String, double[]> e : members.entrySet()) {
            double[] pos = e.getValue();
            double d = DistanceUtil.haversineMeters(lng, lat, pos[0], pos[1]);
            if (d <= radiusMeters) {
                result.add(new GeoMember(e.getKey(), pos[0], pos[1], d));
            }
        }
        result.sort(Comparator.comparingDouble(GeoMember::distanceMeters));
        if (result.size() > count) {
            return result.subList(0, count);
        }
        return result;
    }
}
