package com.carl.mall.dispatch.strategy;

import com.carl.mall.common.dto.AssignResult;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.dto.RiderState;
import com.carl.mall.common.geo.GeoIndex;

import java.util.List;

/**
 * 派单策略抽象（毕设核心）
 *
 * <p>三种实现：</p>
 * <ul>
 *   <li>A：NearestStrategy —— 最近空闲骑手贪心（全量遍历候选）</li>
 *   <li>B：GeoNearestStrategy —— Redis/内存 GEO 索引圈选 3km 候选 + 贪心</li>
 *   <li>C：HungarianBatchStrategy —— 批次全局最优（匈牙利算法最小权指派）</li>
 * </ul>
 */
public interface DispatchStrategy {

    /** 策略名："A" / "B" / "C" */
    String name();

    /**
     * 对一批待派订单做指派。
     *
     * @param orders 待派订单
     * @param riders 在线骑手快照（position / load / acceptCount 为批内一致快照）
     * @param geo    GEO 索引（策略 B 候选检索用）
     * @return 与 orders 一一对应的指派结果（riderId=null 表示本批未指派）
     */
    List<AssignResult> assign(List<OrderTask> orders, List<RiderState> riders, GeoIndex geo);
}
