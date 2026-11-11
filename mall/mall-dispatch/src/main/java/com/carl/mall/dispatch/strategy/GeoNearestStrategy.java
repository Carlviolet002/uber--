package com.carl.mall.dispatch.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.dto.AssignResult;
import com.carl.mall.common.dto.CandidateDetail;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.dto.RiderState;
import com.carl.mall.common.geo.GeoIndex;
import com.carl.mall.common.geo.GeoMember;
import com.carl.mall.dispatch.entity.DispatchConfig;
import com.carl.mall.dispatch.mapper.DispatchConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * 策略 B：GEO 索引圈选候选 + 多因子加权评分，单订单贪心挑选。
 *
 * <p>与策略 A 的唯一区别在候选集来源：B 通过 GEO 索引
 * （mock 模式为内存 GeoIndex，real 模式为 Redis GEO，key=geo:rider:online）
 * 圈选商家周边 3km 内最多 20 名骑手。</p>
 *
 * <p><b>OP-6 多因子评分</b>（各因子归一化到 0~1，越大越优）：
 * <pre>
 *   distScore = 1 / (1 + 距离km)          // 近者优
 *   loadScore = 1 - 当前在途 / 持单上限    // 空闲者优
 *   fairScore = 1 / (1 + 历史接单数)       // 接单少者优（公平性）
 *   score = (wd·distScore + wl·loadScore + wf·fairScore) / (wd+wl+wf)
 * </pre>
 * 权重优先级：t_dispatch_config 热配置（dispatch.bWeight.distance/load/fairness）
 * → yml（mall.dispatch.b-weights.*）→ 代码默认。热配置每次派单批次实时读取，
 * admin 改库后下一批次即生效，无需重启。
 * 注：Rider 无评分/完单率字段，故 rating/completeRate 因子暂缺，仅用距离+负载+公平性。</p>
 *
 * <p>论文口径：GEO 圈选将候选检索从"全量骑手遍历 O(R)"降为
 * "半径查询 O(log R + K)"，K 为圈内骑手数——这正是论文要讲的候选检索成本差异。</p>
 */
@Slf4j
@Component
public class GeoNearestStrategy implements DispatchStrategy {

    private final DispatchConfigMapper configMapper;

    @Value("${mall.dispatch.b-weights.distance:1.0}")
    private double defaultDistanceWeight;
    @Value("${mall.dispatch.b-weights.load:0.3}")
    private double defaultLoadWeight;
    @Value("${mall.dispatch.b-weights.fairness:0.2}")
    private double defaultFairnessWeight;

    public GeoNearestStrategy(DispatchConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @Override
    public String name() {
        return "B";
    }

    /** 读取热权重：t_dispatch_config 优先 → 缺省值（yml / 代码默认） */
    private double hotWeight(String key, double def) {
        try {
            DispatchConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<DispatchConfig>()
                    .eq(DispatchConfig::getCfgKey, key)
                    .last("limit 1"));
            if (cfg != null && cfg.getCfgValue() != null && !cfg.getCfgValue().isBlank()) {
                return Double.parseDouble(cfg.getCfgValue().trim());
            }
        } catch (Exception e) {
            log.debug("[B-weights] 读取 {} 失败，回退默认 {}：{}", key, def, e.getMessage());
        }
        return def;
    }

    @Override
    public List<AssignResult> assign(List<OrderTask> orders, List<RiderState> riders, GeoIndex geo) {
        AssignResult[] results = new AssignResult[orders.size()];
        Map<String, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < orders.size(); i++) {
            orderIndex.put(orders.get(i).getOrderNo(), i);
        }
        if (orders.isEmpty()) {
            return new ArrayList<>();
        }
        if (riders.isEmpty()) {
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

        // 本批权重（热加载一次，供本批所有订单复用）
        double wd = hotWeight("dispatch.bWeight.distance", defaultDistanceWeight);
        double wl = hotWeight("dispatch.bWeight.load", defaultLoadWeight);
        double wf = hotWeight("dispatch.bWeight.fairness", defaultFairnessWeight);
        double wsum = wd + wl + wf;

        // 本批骑手快照：riderId → RiderState
        Map<Long, RiderState> riderMap = new HashMap<>();
        Map<Long, Integer> batchLoad = new HashMap<>();
        for (RiderState rs : riders) {
            riderMap.put(rs.getRiderId(), rs);
            batchLoad.put(rs.getRiderId(), rs.getCurrentLoad());
        }

        List<OrderTask> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator.comparingLong(OrderTask::getCreatedAt));

        // 本批已选中骑手
        Set<Long> selected = new HashSet<>();

        for (OrderTask order : sorted) {
            long t0 = System.nanoTime();

            // 核心差异点：候选集来自 GEO 索引而非全表遍历
            List<GeoMember> geoMembers = geo.radius(
                    CommonConstant.GEO_RIDER_ONLINE,
                    order.getShopLng(), order.getShopLat(),
                    CommonConstant.DISPATCH_RADIUS_METERS, 20);

            List<CandidateDetail> candidates = new ArrayList<>();
            Long bestRiderId = null;
            double bestScore = -1.0;

            for (GeoMember gm : geoMembers) {
                Long riderId;
                try {
                    riderId = Long.parseLong(gm.member()); // member 即 riderId 字符串
                } catch (NumberFormatException e) {
                    log.warn("GEO 成员非法 riderId，跳过: {}", gm.member());
                    continue;
                }
                RiderState rs = riderMap.get(riderId);
                if (rs == null) {
                    continue; // GEO 索引中的历史成员（本批快照无此骑手），跳过
                }
                int load = batchLoad.getOrDefault(riderId, rs.getCurrentLoad());
                if (load >= rs.getCapacity() || selected.contains(riderId)) {
                    continue; // 持单已满或本批已选
                }

                // ===== OP-6 多因子归一化评分 =====
                double distKm = gm.distanceMeters() / 1000.0;
                double distScore = 1.0 / (1.0 + distKm);
                int capacity = rs.getCapacity() <= 0 ? 1 : rs.getCapacity();
                double loadScore = Math.max(0.0, 1.0 - (double) load / capacity);
                double fairScore = 1.0 / (1.0 + Math.max(0, rs.getAcceptCount()));
                double score = wsum > 0
                        ? (wd * distScore + wl * loadScore + wf * fairScore) / wsum
                        : distScore;

                CandidateDetail cd = new CandidateDetail();
                cd.setRiderId(riderId);
                cd.setDistance(gm.distanceMeters());
                cd.setLoad(load);
                cd.setScore(score);
                cd.setSelected(false);
                candidates.add(cd);

                if (score > bestScore) {
                    bestScore = score;
                    bestRiderId = riderId;
                }
            }

            AssignResult result = new AssignResult();
            result.setOrderNo(order.getOrderNo());
            result.setCandidates(candidates);
            if (bestRiderId == null) {
                result.setRiderId(null);
                result.setScore(0);
            } else {
                result.setRiderId(bestRiderId);
                result.setScore(bestScore < 0 ? 0 : bestScore);
                selected.add(bestRiderId);
                batchLoad.merge(bestRiderId, 1, Integer::sum);
                for (CandidateDetail cd : candidates) {
                    if (cd.getRiderId().equals(bestRiderId)) {
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
