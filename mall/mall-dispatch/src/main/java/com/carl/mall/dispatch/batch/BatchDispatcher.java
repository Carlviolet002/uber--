package com.carl.mall.dispatch.batch;

import com.carl.mall.common.dto.AssignResult;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.dto.RiderState;
import com.carl.mall.common.event.EventPublisher;
import com.carl.mall.common.geo.GeoIndex;
import com.carl.mall.common.util.DistanceUtil;
import com.carl.mall.dispatch.client.OrderClient;
import com.carl.mall.dispatch.entity.BatchStat;
import com.carl.mall.dispatch.entity.DispatchRecord;
import com.carl.mall.dispatch.mapper.BatchStatMapper;
import com.carl.mall.dispatch.mapper.DispatchRecordMapper;
import com.carl.mall.dispatch.pending.PendingOrderSource;
import com.carl.mall.dispatch.service.LoadTracker;
import com.carl.mall.dispatch.service.RiderService;
import com.carl.mall.dispatch.service.StrategyService;
import com.carl.mall.dispatch.strategy.DispatchStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 批次调度器（核心）：定时把积压订单 + 在线骑手快照交给当前策略，
 * 落库派单记录/批次统计，并回调订单服务完成指派。
 *
 * <p>并发控制：单实例 ReentrantLock 批次锁；真实集群部署可升级为 Redis SETNX 分布式锁。</p>
 */
@Slf4j
@Component
public class BatchDispatcher {

    private final PendingOrderSource pendingOrderSource;
    private final RiderService riderService;
    private final StrategyService strategyService;
    private final GeoIndex geoIndex;
    private final OrderClient orderClient;
    private final LoadTracker loadTracker;
    private final DispatchRecordMapper dispatchRecordMapper;
    private final BatchStatMapper batchStatMapper;
    private final ObjectMapper objectMapper;
    private final EventPublisher eventPublisher;

    /** 批次锁：防止定时任务与手动触发并发执行（单实例有效） */
    private final ReentrantLock lock = new ReentrantLock();

    public BatchDispatcher(PendingOrderSource pendingOrderSource,
                           RiderService riderService,
                           StrategyService strategyService,
                           GeoIndex geoIndex,
                           OrderClient orderClient,
                           LoadTracker loadTracker,
                           DispatchRecordMapper dispatchRecordMapper,
                           BatchStatMapper batchStatMapper,
                           ObjectMapper objectMapper,
                           EventPublisher eventPublisher) {
        this.pendingOrderSource = pendingOrderSource;
        this.riderService = riderService;
        this.strategyService = strategyService;
        this.geoIndex = geoIndex;
        this.orderClient = orderClient;
        this.loadTracker = loadTracker;
        this.dispatchRecordMapper = dispatchRecordMapper;
        this.batchStatMapper = batchStatMapper;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /** 定时批次派单（启动 15s 后开始，每 5s 一批） */
    @Scheduled(fixedDelayString = "${mall.dispatch.batch-interval-ms:5000}",
            initialDelayString = "${mall.dispatch.batch-initial-delay-ms:15000}")
    public void scheduledBatch() {
        try {
            doBatch();
        } catch (Exception e) {
            log.error("[批次调度] 定时派单异常", e);
        }
    }

    /**
     * 执行一次批次派单（定时与手动触发共用）
     */
    public Map<String, Object> doBatch() {
        // 1) 批次锁：拿不到锁说明另一批次正在执行，直接跳过
        if (!lock.tryLock()) {
            return Map.of("skipped", true);
        }
        try {
            // 2) 派单模式检查：manual 抢单模式跳过自动指派（配置每次批次实时读，admin 改配置 5s 内生效）
            if (strategyService.isManualMode()) {
                log.info("抢单模式，跳过自动派单");
                return Map.of("mode", "manual", "skipped", true);
            }

            // 3) 取出积压订单
            List<OrderTask> orders = pendingOrderSource.poll();
            if (orders == null || orders.isEmpty()) {
                return Map.of("orderCount", 0);
            }

            // 4) 在线骑手快照 + 当前策略
            List<RiderState> riders = riderService.onlineRiders();
            DispatchStrategy strategy = strategyService.current();

            // 5) 计时 + 策略指派
            long t0 = System.currentTimeMillis();
            List<AssignResult> results = strategy.assign(orders, riders, geoIndex);
            long dispatchTimeMs = System.currentTimeMillis() - t0;

            // 6) 批次元信息
            String batchNo = "B" + System.currentTimeMillis();
            int assigned = 0;

            // orderNo → OrderTask 映射（回填 orderId）
            Map<String, OrderTask> taskMap = orders.stream()
                    .collect(Collectors.toMap(OrderTask::getOrderNo, t -> t, (a, b) -> a));

            // 7) 逐结果落库 + 回调订单服务
            for (AssignResult result : results) {
                OrderTask task = taskMap.get(result.getOrderNo());
                if (task == null) {
                    log.warn("[批次调度] 派单结果无法匹配订单: {}", result.getOrderNo());
                    continue;
                }
                // 写派单记录（candidate_details 存 AssignResult 完整 JSON，供消融分析）
                DispatchRecord record = new DispatchRecord();
                record.setOrderId(task.getOrderId());
                record.setBatchNo(batchNo);
                record.setStrategy(strategy.name());
                record.setRiderId(result.getRiderId());
                record.setCandidateCount(result.getCandidates() == null ? 0 : result.getCandidates().size());
                try {
                    record.setCandidateDetails(objectMapper.writeValueAsString(result));
                } catch (Exception e) {
                    log.error("[批次调度] 候选明细序列化失败 orderNo={}", result.getOrderNo(), e);
                }
                record.setDecisionTimeMs((int) result.getDecisionTimeMs());
                record.setCreateTime(LocalDateTime.now());
                dispatchRecordMapper.insert(record);

                if (result.assigned()) {
                    Long riderId = result.getRiderId();
                    try {
                        orderClient.assign(result.getOrderNo(), riderId);
                    } catch (Exception e) {
                        // 回调失败不中断批次，其余订单继续
                        log.error("[批次调度] 回调订单服务指派失败 orderNo={} riderId={}", result.getOrderNo(), riderId, e);
                        continue;
                    }
                    // 本地状态：负载 +1、累计接单 +1
                    loadTracker.inc(riderId);
                    try {
                        riderService.incrAcceptCount(riderId);
                    } catch (Exception e) {
                        log.error("[批次调度] 更新骑手接单数失败 riderId={}", riderId, e);
                    }
                    assigned++;
                    // 发布派单结果事件（mock 打日志 / real 发 x.rider:rider.assigned）
                    eventPublisher.publish("rider.assigned",
                            Map.of("orderNo", result.getOrderNo(), "riderId", riderId));
                }
            }

            // 8) 批次统计：空驶距离 = Σ 各订单最近骑手→商家距离
            double totalEmptyDistance = 0;
            for (OrderTask order : orders) {
                double nearest = Double.MAX_VALUE;
                for (RiderState rs : riders) {
                    double d = DistanceUtil.haversineMeters(
                            rs.getLng(), rs.getLat(), order.getShopLng(), order.getShopLat());
                    if (d < nearest) {
                        nearest = d;
                    }
                }
                if (nearest < Double.MAX_VALUE) {
                    totalEmptyDistance += nearest;
                }
            }
            BatchStat stat = new BatchStat();
            stat.setBatchNo(batchNo);
            stat.setStrategy(strategy.name());
            stat.setOrderCount(orders.size());
            stat.setRiderCount(riders.size());
            stat.setTotalEmptyDistance(totalEmptyDistance);
            stat.setDispatchTimeMs((int) dispatchTimeMs);
            stat.setCreateTime(LocalDateTime.now());
            batchStatMapper.insert(stat);

            // 10) 返回批次摘要
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("batchNo", batchNo);
            summary.put("strategy", strategy.name());
            summary.put("orderCount", orders.size());
            summary.put("assigned", assigned);
            summary.put("dispatchTimeMs", dispatchTimeMs);
            log.info("[批次调度] batchNo={} strategy={} 订单={} 指派成功={} 耗时={}ms",
                    batchNo, strategy.name(), orders.size(), assigned, dispatchTimeMs);
            return summary;
        } finally {
            // 9) 释放批次锁
            lock.unlock();
        }
    }
}
