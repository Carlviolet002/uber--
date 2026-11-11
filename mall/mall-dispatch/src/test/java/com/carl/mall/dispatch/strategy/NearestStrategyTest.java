package com.carl.mall.dispatch.strategy;

import com.carl.mall.common.dto.AssignResult;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.dto.RiderState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 策略 A（最近空闲骑手贪心）单元测试：纯逻辑，无 Spring。
 */
class NearestStrategyTest {

    private final NearestStrategy strategy = new NearestStrategy();

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

    private RiderState rider(long id, double lng, double lat, int capacity, int load) {
        RiderState r = new RiderState();
        r.setRiderId(id);
        r.setLng(lng);
        r.setLat(lat);
        r.setCapacity(capacity);
        r.setCurrentLoad(load);
        r.setAcceptCount(0);
        return r;
    }

    @Test
    void picksNearestRider() {
        // 商家在 (120.0, 30.0)；R1 极近、R2 较远
        OrderTask o = order(120.0, 30.0);
        RiderState r1 = rider(1, 120.0001, 30.0, 5, 0);   // 约 11m
        RiderState r2 = rider(2, 120.05, 30.0, 5, 0);     // 约 5.5km

        List<AssignResult> res = strategy.assign(List.of(o), List.of(r1, r2), null);

        assertEquals(1, res.size());
        assertTrue(res.get(0).assigned());
        assertEquals(1L, res.get(0).getRiderId());
        // 候选明细应含两名骑手，且最近者被标记 selected
        assertEquals(2, res.get(0).getCandidates().size());
        assertTrue(res.get(0).getCandidates().stream()
                .anyMatch(c -> c.getRiderId() == 1L && c.isSelected()));
    }

    @Test
    void overloadedRiderSkipped() {
        OrderTask o = order(120.0, 30.0);
        // R1 满载，R2 空闲
        RiderState r1 = rider(1, 120.0001, 30.0, 1, 1);
        RiderState r2 = rider(2, 120.05, 30.0, 5, 0);

        List<AssignResult> res = strategy.assign(List.of(o), List.of(r1, r2), null);

        assertEquals(2L, res.get(0).getRiderId());
    }

    @Test
    void noRiderYieldsUnassigned() {
        OrderTask o = order(120.0, 30.0);
        List<AssignResult> res = strategy.assign(List.of(o), List.of(), null);
        assertFalse(res.get(0).assigned());
        assertNull(res.get(0).getRiderId());
    }

    @Test
    void emptyOrdersReturnsEmpty() {
        List<AssignResult> res = strategy.assign(List.of(), List.of(rider(1, 120, 30, 5, 0)), null);
        assertNotNull(res);
        assertTrue(res.isEmpty());
    }

    @Test
    void batchAssignsEachOrderDistinctRider() {
        // 两单分别最近不同骑手，验证批内不重复派同一骑手
        OrderTask o1 = order(120.0, 30.0);
        OrderTask o2 = order(120.0, 30.1);
        o2.setOrderNo("O2");
        RiderState r1 = rider(1, 120.0001, 30.0, 5, 0);
        RiderState r2 = rider(2, 120.0001, 30.1, 5, 0);

        List<AssignResult> res = strategy.assign(List.of(o1, o2), List.of(r1, r2), null);

        assertEquals(2, res.size());
        assertTrue(res.stream().allMatch(AssignResult::assigned));
        assertEquals(2L, res.stream().map(AssignResult::getRiderId).distinct().count());
    }
}
