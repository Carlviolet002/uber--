package com.carl.mall.dispatch.strategy;

import com.carl.mall.common.dto.AssignResult;
import com.carl.mall.common.dto.CandidateDetail;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.dto.RiderState;
import com.carl.mall.common.geo.GeoIndex;
import com.carl.mall.common.util.DistanceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 策略 A：最近空闲骑手贪心。
 *
 * <p>每个订单（按 createdAt 升序处理）在全量在线骑手中挑选骑手→商家距离最近、
 * 且当前持单未满（currentLoad &lt; capacity）的骑手；每个骑手一个批次内至多被
 * 选中一次（批内加单计数维护）。</p>
 *
 * <p>论文口径：A 的候选检索是 O(订单数 × 骑手数) 的全表遍历，无索引加速，
 * 与策略 B 的 GEO 圈选形成"候选检索成本"对照组。</p>
 */
@Slf4j
@Component
public class NearestStrategy implements DispatchStrategy {

    @Override
    public String name() {
        return "A";
    }

    @Override
    public List<AssignResult> assign(List<OrderTask> orders, List<RiderState> riders, GeoIndex geo) {
        AssignResult[] results = new AssignResult[orders.size()];
        // 结果必须与入参 orders 一一对应：记录 orderNo → 原始下标
        Map<String, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < orders.size(); i++) {
            orderIndex.put(orders.get(i).getOrderNo(), i);
        }

        if (orders.isEmpty()) {
            return new ArrayList<>();
        }
        if (riders.isEmpty()) {
            // 无在线骑手：全部未指派
            for (OrderTask order : orders) {
                AssignResult r = new AssignResult();
                r.setOrderNo(order.getOrderNo());
                r.setRiderId(null);
                r.setScore(0);
                r.setCandidates(new ArrayList<>());
                r.setDecisionTimeMs(0L);
                results[orderIndex.get(order.getOrderNo())] = r;
            }
            return Arrays.asList(results);
        }

        // 按订单创建时间升序处理，先到先得（排队公平性）
        List<OrderTask> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator.comparingLong(OrderTask::getCreatedAt));

        // 本批已选中骑手（一个批次内每个骑手至多派一单）
        Set<Long> selected = new HashSet<>();
        // 本批加单计数：批内负载 = 快照 currentLoad + 本批已加单数
        Map<Long, Integer> batchLoad = new HashMap<>();
        for (RiderState rs : riders) {
            batchLoad.put(rs.getRiderId(), rs.getCurrentLoad());
        }

        for (OrderTask order : sorted) {
            long t0 = System.nanoTime();

            List<CandidateDetail> candidates = new ArrayList<>();
            RiderState best = null;
            double bestDist = Double.MAX_VALUE;

            // 全量遍历：论文中策略 A 的候选检索成本即在此
            for (RiderState rs : riders) {
                if (selected.contains(rs.getRiderId())) {
                    continue; // 本批已选过
                }
                int load = batchLoad.getOrDefault(rs.getRiderId(), rs.getCurrentLoad());
                if (load >= rs.getCapacity()) {
                    continue; // 持单已满，不可再派
                }
                double dist = DistanceUtil.haversineMeters(
                        rs.getLng(), rs.getLat(), order.getShopLng(), order.getShopLat());
                double score = 1.0 / (1.0 + dist / 1000.0); // 距离越近得分越高
                CandidateDetail cd = new CandidateDetail();
                cd.setRiderId(rs.getRiderId());
                cd.setDistance(dist);
                cd.setLoad(load);
                cd.setScore(score);
                cd.setSelected(false);
                candidates.add(cd);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = rs;
                }
            }

            AssignResult result = new AssignResult();
            result.setOrderNo(order.getOrderNo());
            result.setCandidates(candidates);
            if (best == null) {
                // 无可用骑手（全部满载或已选过）
                result.setRiderId(null);
                result.setScore(0);
            } else {
                result.setRiderId(best.getRiderId());
                result.setScore(1.0 / (1.0 + bestDist / 1000.0));
                // 标记选中 + 批内加单
                selected.add(best.getRiderId());
                batchLoad.merge(best.getRiderId(), 1, Integer::sum);
                for (CandidateDetail cd : candidates) {
                    if (cd.getRiderId().equals(best.getRiderId())) {
                        cd.setSelected(true);
                    }
                }
            }
            result.setDecisionTimeMs((System.nanoTime() - t0) / 1_000_000L);
            results[orderIndex.get(order.getOrderNo())] = result;
        }

        return Arrays.asList(results);
    }
}
