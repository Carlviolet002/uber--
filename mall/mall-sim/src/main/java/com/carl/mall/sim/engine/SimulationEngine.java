package com.carl.mall.sim.engine;

import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.dto.AssignResult;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.dto.RiderState;
import com.carl.mall.common.geo.GeoIndex;
import com.carl.mall.common.util.DistanceUtil;
import com.carl.mall.dispatch.strategy.DispatchStrategy;
import com.carl.mall.dispatch.strategy.GeoNearestStrategy;
import com.carl.mall.dispatch.strategy.HungarianBatchStrategy;
import com.carl.mall.dispatch.strategy.NearestStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 派单策略离线仿真引擎。
 *
 * <p>仿真口径（与论文一致，全部为简化假设）：</p>
 * <ul>
 *   <li>区域：5km×5km（约 ±0.025°），3 个固定商家；订单目的地在商家 ±0.02° 内均匀随机；</li>
 *   <li>距离：Haversine 直线距离（替代真实路网）；骑手匀速直线骑行 250 m/min；</li>
 *   <li>时间轴：1 分钟一步，共 120 分钟；每分钟生成 k ~ Poisson(lambda) 个订单
 *       （Knuth 算法），订单承诺时长固定 30 分钟；</li>
 *   <li>派单：每分钟把"未指派订单"队列交给策略（传入骑手位置/负载/接单数快照）；
 *       一个批次（一分钟）内每个骑手至多被派一单；</li>
 *   <li>履约：指派即起算，完成时间 = 当前时刻 + (骑手→商家 + 商家→地址)/250 分钟；
 *       骑手完成所有在途订单后负载清零，位置更新为最后一个订单目的地；</li>
 *   <li>指标：订单创建→完成总时长（含排队等待）、超时 = 时长 &gt; 30min、
 *       空驶距离 = Σ 骑手接单位置→商家、决策耗时 = 策略实测毫秒均值。
 *       注：均值/超时率只统计在仿真结束前完成的订单。</li>
 * </ul>
 */
@Slf4j
@Component
public class SimulationEngine {

    /** 骑手直线骑行速度（米/分钟） */
    private static final double SPEED_METERS_PER_MIN = 250.0;
    /** 骑手同时持单上限 */
    private static final int CAPACITY = 3;
    /** 仿真时长（分钟） */
    private static final int TOTAL_MINUTES = 120;
    /** 承诺配送时长（分钟） */
    private static final int PROMISE_MINUTES = 30;
    /** 区域中心（郑州商圈附近） */
    private static final double REGION_CENTER_LNG = 113.658;
    private static final double REGION_CENTER_LAT = 34.763;
    /** 5km×5km 区域：约 ±0.025° */
    private static final double REGION_HALF_DEGREE = 0.025;
    /** 3 个固定商家坐标 */
    private static final double[][] SHOPS = {
            {113.658, 34.763},
            {113.661, 34.758},
            {113.655, 34.766}
    };
    /** 订单目的地在商家 ±0.02° 内随机 */
    private static final double DEST_DELTA = 0.02;

    private static final List<String> STRATEGIES = List.of("A", "B", "C");
    private static final int[] RIDER_COUNTS = {5, 10, 20};
    private static final double[] LAMBDAS = {2.0, 4.0};
    private static final String[] CSV_HEADER = {
            "strategy", "riderCount", "lambda", "orderCount",
            "avgDeliveryMin", "timeoutRate", "emptyDistanceKm", "avgDecisionMs"
    };

    private final NearestStrategy nearestStrategy;
    private final GeoNearestStrategy geoNearestStrategy;
    private final HungarianBatchStrategy hungarianBatchStrategy;
    private final GeoIndex geoIndex;

    public SimulationEngine(NearestStrategy nearestStrategy,
                            GeoNearestStrategy geoNearestStrategy,
                            HungarianBatchStrategy hungarianBatchStrategy,
                            GeoIndex geoIndex) {
        this.nearestStrategy = nearestStrategy;
        this.geoNearestStrategy = geoNearestStrategy;
        this.hungarianBatchStrategy = hungarianBatchStrategy;
        this.geoIndex = geoIndex;
    }

    /** 跑完 3×3×2=18 组配置，输出 CSV 并打印汇总表 */
    public void run() {
        List<String[]> rows = new ArrayList<>();
        rows.add(CSV_HEADER);
        int configIndex = 0;
        for (String strategyName : STRATEGIES) {
            for (int riderCount : RIDER_COUNTS) {
                for (double lambda : LAMBDAS) {
                    // 每种配置固定种子（42 + 序号），保证实验可复现
                    long seed = 42L + configIndex++;
                    rows.add(simulate(strategyName, riderCount, lambda, seed));
                }
            }
        }
        writeCsv(rows);
        printTable(rows);
    }

    /** 单组配置仿真，返回一行 CSV 数据 */
    private String[] simulate(String strategyName, int riderCount, double lambda, long seed) {
        Random rnd = new Random(seed);
        DispatchStrategy strategy = resolve(strategyName);

        // 骑手初始随机分布（同一 Random(seed) 派生，保证不同策略下骑手初始条件一致）
        Map<Long, SimRider> riders = new LinkedHashMap<>();
        for (int i = 1; i <= riderCount; i++) {
            double lng = REGION_CENTER_LNG + (rnd.nextDouble() * 2 - 1) * REGION_HALF_DEGREE;
            double lat = REGION_CENTER_LAT + (rnd.nextDouble() * 2 - 1) * REGION_HALF_DEGREE;
            riders.put((long) i, new SimRider(lng, lat));
        }

        Deque<SimOrder> pending = new ArrayDeque<>(); // 未指派订单（含排队）
        long orderSeq = 0;
        int created = 0;
        int completed = 0;
        int timeout = 0;
        double totalDeliveryMin = 0;
        double emptyDistanceMeters = 0;
        long totalDecisionMs = 0;
        int decisionSamples = 0;

        for (int t = 0; t < TOTAL_MINUTES; t++) {
            // ---- 1) 结算本分钟到期的在途订单：负载清零 + 位置更新为最后一个订单目的地 ----
            for (SimRider rider : riders.values()) {
                double lastDestLng = 0, lastDestLat = 0;
                double lastCompleteMin = -1;
                Iterator<SimDelivery> it = rider.inTransit.iterator();
                while (it.hasNext()) {
                    SimDelivery d = it.next();
                    if (d.completeMin <= t) {
                        it.remove();
                        if (d.completeMin >= lastCompleteMin) {
                            lastCompleteMin = d.completeMin;
                            lastDestLng = d.destLng;
                            lastDestLat = d.destLat;
                        }
                        double duration = d.completeMin - d.order.createMin; // 创建→完成（含排队）
                        totalDeliveryMin += duration;
                        completed++;
                        if (duration > PROMISE_MINUTES) {
                            timeout++;
                        }
                    }
                }
                rider.load = rider.inTransit.size();
                if (lastCompleteMin >= 0) {
                    rider.lng = lastDestLng;
                    rider.lat = lastDestLat;
                }
            }

            // ---- 2) 生成 k ~ Poisson(lambda) 个新订单（Knuth 算法） ----
            int k = poisson(rnd, lambda);
            for (int i = 0; i < k; i++) {
                int shopIdx = rnd.nextInt(SHOPS.length);
                double shopLng = SHOPS[shopIdx][0];
                double shopLat = SHOPS[shopIdx][1];
                OrderTask task = new OrderTask();
                task.setOrderId(++orderSeq);
                task.setOrderNo("SIM" + orderSeq);
                task.setShopId((long) (shopIdx + 1));
                task.setShopLng(shopLng);
                task.setShopLat(shopLat);
                task.setAddressLng(shopLng + (rnd.nextDouble() * 2 - 1) * DEST_DELTA);
                task.setAddressLat(shopLat + (rnd.nextDouble() * 2 - 1) * DEST_DELTA);
                task.setExpectedMinutes(PROMISE_MINUTES);
                task.setCreatedAt(t);
                pending.add(new SimOrder(task, t));
                created++;
            }

            // ---- 3) 每分钟一次批次派单：未指派订单 + 骑手当前快照 → 策略 ----
            if (!pending.isEmpty()) {
                List<OrderTask> tasks = new ArrayList<>(pending.size());
                for (SimOrder so : pending) {
                    tasks.add(so.task);
                }
                List<RiderState> states = new ArrayList<>(riders.size());
                for (Map.Entry<Long, SimRider> e : riders.entrySet()) {
                    states.add(e.getValue().toState(e.getKey()));
                }
                // 策略 B 依赖 GEO 索引：派单前先把全部骑手当前位置写入
                if ("B".equals(strategyName)) {
                    for (RiderState st : states) {
                        geoIndex.add(CommonConstant.GEO_RIDER_ONLINE,
                                st.getLng(), st.getLat(), String.valueOf(st.getRiderId()));
                    }
                }
                List<AssignResult> results = strategy.assign(tasks, states, geoIndex);

                // 决策耗时采样（策略内部已实测填入 decisionTimeMs）
                for (AssignResult res : results) {
                    totalDecisionMs += res.getDecisionTimeMs();
                    decisionSamples++;
                }

                // 处理指派结果：成功 → 负载+1、记录完成时间与空驶距离；失败 → 留下批重试
                Deque<SimOrder> stillPending = new ArrayDeque<>();
                int idx = 0;
                for (SimOrder so : pending) {
                    AssignResult res = idx < results.size() ? results.get(idx) : null;
                    idx++;
                    boolean taken = false;
                    if (res != null && res.assigned()) {
                        SimRider rider = riders.get(res.getRiderId());
                        if (rider != null && rider.load < rider.capacity) {
                            OrderTask task = so.task;
                            double pickupDist = DistanceUtil.haversineMeters(
                                    rider.lng, rider.lat, task.getShopLng(), task.getShopLat());
                            double deliverDist = DistanceUtil.haversineMeters(
                                    task.getShopLng(), task.getShopLat(),
                                    task.getAddressLng(), task.getAddressLat());
                            double travelMin = (pickupDist + deliverDist) / SPEED_METERS_PER_MIN;
                            rider.inTransit.add(new SimDelivery(so, t + travelMin,
                                    task.getAddressLng(), task.getAddressLat()));
                            rider.load++;
                            rider.acceptCount++;
                            emptyDistanceMeters += pickupDist; // 骑手接单位置→商家 = 空驶
                            taken = true;
                        }
                    }
                    if (!taken) {
                        stillPending.add(so);
                    }
                }
                pending = stillPending;
            }
        }

        // ---- 指标汇总 ----
        double avgDeliveryMin = completed > 0 ? totalDeliveryMin / completed : 0;
        double timeoutRate = completed > 0 ? (double) timeout / completed : 0;
        double emptyKm = emptyDistanceMeters / 1000.0;
        double avgDecisionMs = decisionSamples > 0 ? (double) totalDecisionMs / decisionSamples : 0;

        log.info("[SIM] strategy={} riders={} lambda={} 创建={} 完成={} 平均时长={}min 超时率={} 空驶={}km 平均决策={}ms",
                strategyName, riderCount, fmt(lambda), created, completed,
                fmt(avgDeliveryMin), fmt(timeoutRate), fmt(emptyKm), fmt(avgDecisionMs));

        return new String[]{
                strategyName,
                String.valueOf(riderCount),
                fmt(lambda),
                String.valueOf(created),
                fmt(avgDeliveryMin),
                fmt(timeoutRate),
                fmt(emptyKm),
                fmt(avgDecisionMs)
        };
    }

    private DispatchStrategy resolve(String name) {
        return switch (name) {
            case "A" -> nearestStrategy;
            case "B" -> geoNearestStrategy;
            case "C" -> hungarianBatchStrategy;
            default -> throw new IllegalArgumentException("未知策略: " + name);
        };
    }

    /** Knuth 泊松采样：k ~ Poisson(lambda)（lambda=2/4 时 k 可能较大，循环上界由概率自然保证） */
    private int poisson(Random rnd, double lambda) {
        double l = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rnd.nextDouble();
        } while (p > l);
        return k - 1;
    }

    /** 结果写 ./sim-output/sim-result.csv（覆盖写：表头 + 18 行） */
    private void writeCsv(List<String[]> rows) {
        File dir = new File("sim-output");
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("[SIM] 输出目录创建失败: {}", dir.getAbsolutePath());
        }
        File file = new File(dir, "sim-result.csv");
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (String[] row : rows) {
                writer.println(String.join(",", row));
            }
        } catch (IOException e) {
            log.error("[SIM] 写 CSV 失败", e);
            return;
        }
        log.info("[SIM] 结果已写入 {}", file.getAbsolutePath());
    }

    /** 控制台打印汇总表 */
    private void printTable(List<String[]> rows) {
        String format = "%-8s %-10s %-6s %-10s %-13s %-11s %-14s %-13s%n";
        System.out.println();
        System.out.println("==================== 派单策略仿真结果汇总（18 组配置） ====================");
        System.out.printf(format, (Object[]) CSV_HEADER);
        for (int i = 1; i < rows.size(); i++) {
            System.out.printf(format, (Object[]) rows.get(i));
        }
        System.out.println("=========================================================================");
        System.out.println("说明: avgDeliveryMin=创建→完成平均时长(含排队); timeoutRate=时长>30min 占比;");
        System.out.println("      emptyDistanceKm=总空驶距离; avgDecisionMs=每次派单实测决策耗时均值。");
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ==================== 内部数据结构 ====================

    /** 仿真骑手 */
    private static class SimRider {
        double lng;
        double lat;
        int load;
        int acceptCount;
        final int capacity = CAPACITY;
        final List<SimDelivery> inTransit = new ArrayList<>();

        SimRider(double lng, double lat) {
            this.lng = lng;
            this.lat = lat;
        }

        RiderState toState(Long riderId) {
            RiderState s = new RiderState();
            s.setRiderId(riderId);
            s.setName("骑手" + riderId);
            s.setLng(lng);
            s.setLat(lat);
            s.setCapacity(capacity);
            s.setCurrentLoad(load);
            s.setAcceptCount(acceptCount);
            return s;
        }
    }

    /** 仿真订单 */
    private static class SimOrder {
        final OrderTask task;
        final double createMin; // 创建时间（分钟）

        SimOrder(OrderTask task, double createMin) {
            this.task = task;
            this.createMin = createMin;
        }
    }

    /** 在途配送单（预计完成时间 + 目的地） */
    private static class SimDelivery {
        final SimOrder order;
        final double completeMin;
        final double destLng;
        final double destLat;

        SimDelivery(SimOrder order, double completeMin, double destLng, double destLat) {
            this.order = order;
            this.completeMin = completeMin;
            this.destLng = destLng;
            this.destLat = destLat;
        }
    }
}
