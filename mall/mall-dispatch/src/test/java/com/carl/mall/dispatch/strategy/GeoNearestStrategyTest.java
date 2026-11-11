package com.carl.mall.dispatch.strategy;

import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.dto.AssignResult;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.dto.RiderState;
import com.carl.mall.common.geo.MockGeoIndex;
import com.carl.mall.dispatch.mapper.DispatchConfigMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

/**
 * 策略 B（GEO 圈选 + 多因子加权）单元测试：
 * 用内存 MockGeoIndex 替代 Redis，用 Mockito 桩掉配置表读取走默认权重。
 */
class GeoNearestStrategyTest {

    private GeoNearestStrategy build() {
        DispatchConfigMapper mapper = Mockito.mock(DispatchConfigMapper.class);
        Mockito.when(mapper.selectOne(any())).thenReturn(null); // 无热配置 -> 走默认权重
        GeoNearestStrategy s = new GeoNearestStrategy(mapper);
        ReflectionTestUtils.setField(s, "defaultDistanceWeight", 1.0);
        ReflectionTestUtils.setField(s, "defaultLoadWeight", 0.3);
        ReflectionTestUtils.setField(s, "defaultFairnessWeight", 0.2);
        return s;
    }

    private OrderTask order(double shopLng, double shopLat) {
        OrderTask o = new OrderTask();
        o.setOrderNo("O1");
        o.setShopLng(shopLng);
        o.setShopLat(shopLat);
        o.setAddressLng(shopLng + 0.001);
        o.setAddressLat(shopLat);
        o.setCreatedAt(1000L);
        return o;
    }

    private RiderState rider(long id, double lng, double lat, int cap, int load) {
        RiderState r = new RiderState();
        r.setRiderId(id);
        r.setLng(lng);
        r.setLat(lat);
        r.setCapacity(cap);
        r.setCurrentLoad(load);
        r.setAcceptCount(0);
        return r;
    }

    @Test
    void geoRadiusFiltersCandidatesOutsideRadius() {
        GeoNearestStrategy s = build();
        MockGeoIndex geo = new MockGeoIndex();
        // R1 在商家处（0m，圈内），R2 在 5km 外（超出 3km 圈）
        geo.add(CommonConstant.GEO_RIDER_ONLINE, 120.0, 30.0, "1");
        geo.add(CommonConstant.GEO_RIDER_ONLINE, 120.05, 30.0, "2");

        OrderTask o = order(120.0, 30.0);
        RiderState r1 = rider(1, 120.0, 30.0, 5, 0);
        RiderState r2 = rider(2, 120.05, 30.0, 5, 0);

        List<AssignResult> res = s.assign(List.of(o), List.of(r1, r2), geo);

        // 候选应只含圈内 R1，R2 被 GEO 索引过滤
        assertEquals(1L, res.get(0).getRiderId());
        assertEquals(1, res.get(0).getCandidates().size());
    }

    @Test
    void multiFactorPrefersLessLoadedWhenNearlyEquidistant() {
        GeoNearestStrategy s = build();
        MockGeoIndex geo = new MockGeoIndex();
        // 两名骑手都在圈内：R1 极近但满载(4/5)，R2 略远但空闲(0/5)
        geo.add(CommonConstant.GEO_RIDER_ONLINE, 120.0, 30.0, "1");
        geo.add(CommonConstant.GEO_RIDER_ONLINE, 120.001, 30.0, "2");

        OrderTask o = order(120.0, 30.0);
        RiderState r1 = rider(1, 120.0, 30.0, 5, 4);
        RiderState r2 = rider(2, 120.001, 30.0, 5, 0);

        List<AssignResult> res = s.assign(List.of(o), List.of(r1, r2), geo);

        // 多因子评分：R1 distScore 高但 loadScore 低(0.2)，R2 空闲占优 -> 选 R2
        assertEquals(2L, res.get(0).getRiderId());
    }

    @Test
    void noCandidateInRadiusYieldsUnassigned() {
        GeoNearestStrategy s = build();
        MockGeoIndex geo = new MockGeoIndex(); // 空索引
        OrderTask o = order(120.0, 30.0);
        RiderState r1 = rider(1, 120.05, 30.0, 5, 0);
        List<AssignResult> res = s.assign(List.of(o), List.of(r1), geo);
        assertEquals(false, res.get(0).assigned());
    }
}
