package com.carl.mall.common.geo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis GEO 索引（real 模式）。
 * 注意：Redis 5.0.7 只有 GEORADIUS，代码锁定 GEORADIUS，不用 GEOSEARCH（6.2+）。
 */
@Component
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
public class RedisGeoIndex implements GeoIndex {

    private final StringRedisTemplate redisTemplate;

    public RedisGeoIndex(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void add(String key, double lng, double lat, String member) {
        redisTemplate.opsForGeo().add(key, new Point(lng, lat), member);
    }

    @Override
    public void remove(String key, String member) {
        redisTemplate.opsForGeo().remove(key, member);
    }

    @Override
    public List<GeoMember> radius(String key, double lng, double lat, double radiusMeters, int count) {
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance()
                .includeCoordinates()
                .sortAscending()
                .limit(count);
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().radius(key,
                new Circle(new Point(lng, lat), new Distance(radiusMeters, RedisGeoCommands.DistanceUnit.METERS)),
                args);
        List<GeoMember> list = new ArrayList<>();
        if (results == null) {
            return list;
        }
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results.getContent()) {
            RedisGeoCommands.GeoLocation<String> loc = r.getContent();
            Point p = loc.getPoint();
            double dist = r.getDistance() == null ? 0 : r.getDistance().getValue();
            list.add(new GeoMember(loc.getName(), p.getX(), p.getY(), dist));
        }
        return list;
    }
}
