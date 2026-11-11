package com.carl.mall.dispatch.strategy;

import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.dto.AssignResult;
import com.carl.mall.common.dto.CandidateDetail;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.dto.RiderState;
import com.carl.mall.common.geo.GeoIndex;
import com.carl.mall.common.util.DistanceUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略 C：批次全局最优（匈牙利算法最小权指派）。
 *
 * <p>与 A/B 的"逐单贪心"不同，C 把本批次订单×骑手建模为带权二分图的最小权匹配：
 * 目标是让整批的总代价（取餐空驶 + 配送里程 + 负载惩罚 + …）最小，允许"局部让利、全局最优"。</p>
 *
 * <p>代价矩阵说明（论文 4.x 节核心）：</p>
 * <pre>
 *   cost[i][j] = w1 * dist(骑手j → 商家i)         取餐空驶距离（主项）
 *              + w2 * dist(商家i → 用户i)         配送距离
 *              + w3 * 骑手j当前在途负载             负载惩罚（单位换算为"米"）
 *              + w4 * slack                       时间宽裕度惩罚
 *              + w5 * (1 - 1/(acceptCount+1))     公平性惩罚（接单多者被惩罚）
 *   slack = max(0, estimatedMinutes - expectedMinutes) * 10
 *   estimatedMinutes = (dist(骑手→商家) + dist(商家→用户)) / 250.0  （直线骑行 250m/min）
 *   骑手 currentLoad >= capacity 的对 → cost = INF（不可指派）
 * </pre>
 *
 * <p>两个工程保护（论文"算法工程化"素材）：</p>
 * <ul>
 *   <li>n≠m 时补虚拟骑手 + INF 占位：匈牙利算法要求 n&lt;=m，
 *       当订单数 n &gt; 骑手数 m 时补 (n-m) 个虚拟骑手列（全 INF），
 *       算法退化为 n×n 方阵求解；最终代价为 INF 的配对视为未指派；</li>
 *   <li>矩阵上限 max-matrix（默认 20×20）截断保护：O(n³) 的匈牙利算法对超大矩阵不友好，
 *       超过上限的订单本轮不参与优化（riderId=null），留待下一批次。</li>
 * </ul>
 */
@Slf4j
@Component
public class HungarianBatchStrategy implements DispatchStrategy {

    /** 非法配对的代价占位（视为无穷大） */
    private static final double INF = 1e9;

    /** 直线骑行速度（米/分钟），与仿真器口径一致 */
    private static final double SPEED_METERS_PER_MIN = 250.0;

    /** w1：骑手→商家取餐距离权重 */
    @Value("${mall.dispatch.weights.w1:1.0}")
    private double w1;

    /** w2：商家→用户配送距离权重 */
    @Value("${mall.dispatch.weights.w2:0.5}")
    private double w2;

    /** w3：在途负载惩罚权重（单位换算成"米"） */
    @Value("${mall.dispatch.weights.w3:500.0}")
    private double w3;

    /** w4：时间宽裕度权重 */
    @Value("${mall.dispatch.weights.w4:0.0}")
    private double w4;

    /** w5：公平性权重 */
    @Value("${mall.dispatch.weights.w5:0.0}")
    private double w5;

    /** 代价矩阵最大规模（订单数上限，截断保护） */
    @Value("${mall.dispatch.max-matrix:20}")
    private int maxMatrix;

    @Override
    public String name() {
        return "C";
    }

    @Override
    public List<AssignResult> assign(List<OrderTask> orders, List<RiderState> riders, GeoIndex geo) {
        long t0 = System.nanoTime();
        long batchMs;

        AssignResult[] results = new AssignResult[orders.size()];
        Map<String, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < orders.size(); i++) {
            orderIndex.put(orders.get(i).getOrderNo(), i);
        }
        if (orders.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 按创建时间升序，截断时保留最早下单的 max-matrix 个
            List<OrderTask> sorted = new ArrayList<>(orders);
            sorted.sort(Comparator.comparingLong(OrderTask::getCreatedAt));

            int m = riders.size();
            int n = Math.min(sorted.size(), maxMatrix);

            // 截断保护：超过矩阵上限的订单本轮不参与优化
            for (int k = n; k < sorted.size(); k++) {
                OrderTask over = sorted.get(k);
                AssignResult r = new AssignResult();
                r.setOrderNo(over.getOrderNo());
                r.setRiderId(null);
                r.setScore(0);
                r.setCandidates(new ArrayList<>());
                r.setDecisionTimeMs(0L);
                results[orderIndex.get(over.getOrderNo())] = r;
                log.warn("[策略C] 订单 {} 超出矩阵上限 {}，本批未参与指派，留待下批", over.getOrderNo(), maxMatrix);
            }

            List<OrderTask> active = sorted.subList(0, n);

            // 骑手快照
            Map<Long, RiderState> riderMap = new HashMap<>();
            for (RiderState rs : riders) {
                riderMap.put(rs.getRiderId(), rs);
            }

            if (m == 0) {
                // 无在线骑手：全部未指派（候选明细为空）
                for (OrderTask order : active) {
                    AssignResult r = new AssignResult();
                    r.setOrderNo(order.getOrderNo());
                    r.setRiderId(null);
                    r.setScore(0);
                    r.setCandidates(new ArrayList<>());
                    r.setDecisionTimeMs(0L);
                    results[orderIndex.get(order.getOrderNo())] = r;
                }
                batchMs = (System.nanoTime() - t0) / 1_000_000L;
                fillBatchTime(results, batchMs);
                return Arrays.asList(results);
            }

            // 预计算：取餐距离矩阵与完整代价矩阵（仅真实骑手列）
            double[][] pickupDist = new double[n][m];
            double[][] costReal = new double[n][m];
            for (int i = 0; i < n; i++) {
                OrderTask order = active.get(i);
                // 承诺时长默认 30 分钟兜底
                int expectedMinutes = order.getExpectedMinutes() > 0
                        ? order.getExpectedMinutes() : CommonConstant.DEFAULT_PROMISE_MINUTES;
                double deliverDist = DistanceUtil.haversineMeters(
                        order.getShopLng(), order.getShopLat(), order.getAddressLng(), order.getAddressLat());
                for (int j = 0; j < m; j++) {
                    RiderState rs = riders.get(j);
                    double dPickup = DistanceUtil.haversineMeters(
                            rs.getLng(), rs.getLat(), order.getShopLng(), order.getShopLat());
                    pickupDist[i][j] = dPickup;
                    if (rs.getCurrentLoad() >= rs.getCapacity()) {
                        costReal[i][j] = INF; // 满载骑手不可指派
                        continue;
                    }
                    // 时间宽裕度惩罚：直线骑行时长超出承诺时长的部分 ×10
                    double estimatedMinutes = (dPickup + deliverDist) / SPEED_METERS_PER_MIN;
                    double slack = Math.max(0.0, estimatedMinutes - expectedMinutes) * 10.0;
                    // 公平性：历史接单越多，惩罚越大（接单少的骑手获得倾斜）
                    double fairness = 1.0 - 1.0 / (rs.getAcceptCount() + 1);
                    costReal[i][j] = w1 * dPickup
                            + w2 * deliverDist
                            + w3 * rs.getCurrentLoad()
                            + w4 * slack
                            + w5 * fairness;
                }
            }

            // 工程保护：整行全 INF 的订单（无任何可用骑手）不参与求解，
            // 否则 e-maxx 迭代中 INF-INF 会产生 NaN/越界；此类订单直接判为未指派。
            List<Integer> rows = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                boolean anyFinite = false;
                for (int j = 0; j < m; j++) {
                    if (costReal[i][j] < INF) {
                        anyFinite = true;
                        break;
                    }
                }
                if (anyFinite) {
                    rows.add(i);
                }
            }

            // n≠m 处理：参与行数 > 骑手数时补虚拟骑手列（全 INF）到方阵；
            // 参与行数 <= 骑手数时直接 n×m 求解（匈牙利算法要求 n<=m 已满足）。
            int r = rows.size();
            int size = Math.max(r, m);
            double[][] cost = new double[r][size];
            for (int a = 0; a < r; a++) {
                int originalRow = rows.get(a);
                for (int j = 0; j < m; j++) {
                    cost[a][j] = costReal[originalRow][j];
                }
                for (int j = m; j < size; j++) {
                    cost[a][j] = INF; // 虚拟骑手：任何订单配对其代价均为 INF
                }
            }

            // 每个参与订单指派到的骑手下标（-1 表示未指派）
            int[] assignedRider = new int[n];
            Arrays.fill(assignedRider, -1);
            if (r > 0) {
                int[] p = HungarianAlgorithm.solve(cost);
                for (int a = 0; a < r; a++) {
                    int originalRow = rows.get(a);
                    int col = -1;
                    for (int j = 1; j <= size; j++) {
                        if (p[j] == a + 1) {
                            col = j - 1;
                            break;
                        }
                    }
                    // 真实骑手列且代价非 INF 才算指派成功
                    if (col >= 0 && col < m && cost[a][col] < INF) {
                        assignedRider[originalRow] = col;
                    }
                }
            }

            // 组装结果：候选明细覆盖全部真实骑手（消融分析需要完整得分）
            for (int i = 0; i < n; i++) {
                OrderTask order = active.get(i);
                List<CandidateDetail> candidates = new ArrayList<>(m);
                for (int j = 0; j < m; j++) {
                    CandidateDetail cd = new CandidateDetail();
                    cd.setRiderId(riders.get(j).getRiderId());
                    cd.setDistance(pickupDist[i][j]);
                    cd.setLoad(riders.get(j).getCurrentLoad());
                    // 代价归一化为得分（代价越小得分越高）
                    cd.setScore(1.0 / (1.0 + costReal[i][j] / 1000.0));
                    cd.setSelected(assignedRider[i] == j);
                    candidates.add(cd);
                }
                AssignResult result = new AssignResult();
                result.setOrderNo(order.getOrderNo());
                result.setCandidates(candidates);
                if (assignedRider[i] >= 0) {
                    result.setRiderId(riders.get(assignedRider[i]).getRiderId());
                    result.setScore(1.0 / (1.0 + costReal[i][assignedRider[i]] / 1000.0));
                } else {
                    result.setRiderId(null);
                    result.setScore(0);
                }
                result.setDecisionTimeMs(0L);
                results[orderIndex.get(order.getOrderNo())] = result;
            }
        } finally {
            // 整批一次计时：每个订单的 decisionTimeMs 填批次总耗时
            batchMs = (System.nanoTime() - t0) / 1_000_000L;
        }
        fillBatchTime(results, batchMs);
        return Arrays.asList(results);
    }

    /** 统一填充批次决策耗时（策略 C 为整批一次性求解，各订单共享批次耗时） */
    private void fillBatchTime(AssignResult[] results, long batchMs) {
        for (AssignResult r : results) {
            if (r != null && r.getDecisionTimeMs() == 0L) {
                r.setDecisionTimeMs(batchMs);
            }
        }
    }
}
