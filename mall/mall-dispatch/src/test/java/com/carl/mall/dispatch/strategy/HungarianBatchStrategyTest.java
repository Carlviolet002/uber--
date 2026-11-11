package com.carl.mall.dispatch.strategy;

import com.carl.mall.common.dto.AssignResult;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.dto.RiderState;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 策略 C（匈牙利算法批次全局最优）单元测试：纯逻辑，权重经反射注入。
 */
class HungarianBatchStrategyTest {

    private HungarianBatchStrategy build() {
        HungarianBatchStrategy s = new HungarianBatchStrategy();
        ReflectionTestUtils.setField(s, "w1", 1.0);
        ReflectionTestUtils.setField(s, "w2", 0.5);
        ReflectionTestUtils.setField(s, "w3", 0.0);
        ReflectionTestUtils.setField(s, "w4", 0.0);
        ReflectionTestUtils.setField(s, "w5", 0.0);
        ReflectionTestUtils.setField(s, "maxMatrix", 20);
        return s;
    }

    private OrderTask order(String no, double shopLng, double shopLat, double addrLng, double addrLat) {
        OrderTask o = new OrderTask();
        o.setOrderNo(no);
        o.setShopLng(shopLng);
        o.setShopLat(shopLat);
        o.setAddressLng(addrLng);
        o.setAddressLat(addrLat);
        o.setExpectedMinutes(30);
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
    void globalOptimalPairsEachOrderToNearestRider() {
        // O1 近 R1、O2 近 R2 -> 全局最优即各自配对最近骑手
        OrderTask o1 = order("O1", 0.0, 0.0, 0.001, 0.0);
        OrderTask o2 = order("O2", 0.0, 0.01, 0.001, 0.01);
        RiderState r1 = rider(1, 0.0001, 0.0, 5, 0);
        RiderState r2 = rider(2, 0.0001, 0.01, 5, 0);

        List<AssignResult> res = build().assign(List.of(o1, o2), List.of(r1, r2), null);

        Map<String, Long> map = res.stream()
                .collect(Collectors.toMap(AssignResult::getOrderNo, AssignResult::getRiderId));
        assertEquals(1L, map.get("O1"));
        assertEquals(2L, map.get("O2"));
    }

    @Test
    void overloadedRiderNeverAssigned() {
        OrderTask o = order("O1", 0.0, 0.0, 0.001, 0.0);
        RiderState r1 = rider(1, 0.0001, 0.0, 1, 1); // 满载
        List<AssignResult> res = build().assign(List.of(o), List.of(r1), null);
        assertFalse(res.get(0).assigned());
    }

    @Test
    void moreOrdersThanRidersAssignsAtMostOnePerRider() {
        // 2 单 1 骑手 -> 恰好 1 单被指派（虚拟骑手列兜底）
        OrderTask o1 = order("O1", 0.0, 0.0, 0.001, 0.0);
        OrderTask o2 = order("O2", 0.0, 0.0, 0.002, 0.0);
        RiderState r1 = rider(1, 0.0001, 0.0, 5, 0);
        List<AssignResult> res = build().assign(List.of(o1, o2), List.of(r1), null);
        assertEquals(1, res.stream().filter(AssignResult::assigned).count());
    }

    @Test
    void resultOrderMatchesInputOrder() {
        OrderTask o1 = order("O1", 0.0, 0.0, 0.001, 0.0);
        OrderTask o2 = order("O2", 0.0, 0.01, 0.001, 0.01);
        RiderState r1 = rider(1, 0.0001, 0.0, 5, 0);
        RiderState r2 = rider(2, 0.0001, 0.01, 5, 0);
        List<AssignResult> res = build().assign(List.of(o1, o2), List.of(r1, r2), null);
        // 结果顺序须与入参订单一一对应
        assertEquals("O1", res.get(0).getOrderNo());
        assertEquals("O2", res.get(1).getOrderNo());
    }
}
