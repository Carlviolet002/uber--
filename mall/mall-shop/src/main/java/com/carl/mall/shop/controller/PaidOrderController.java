package com.carl.mall.shop.controller;

import com.carl.mall.common.result.Result;
import com.carl.mall.shop.service.PaidOrderNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 已支付待接单订单号查询接口（D6-A，仅 real 模式生效，本轮联调验证用，R3 可选联动前端）。
 * GET /api/shop/orders/pending-paid?shopId=1 → Redis `shop:paid:{shopId}` 中的订单号列表。
 */
@RestController
@RequestMapping("/shop/orders")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mall.mock.enabled", havingValue = "false")
public class PaidOrderController {

    private final PaidOrderNoticeService paidOrderNoticeService;

    @GetMapping("/pending-paid")
    public Result<List<String>> pendingPaid(@RequestParam Long shopId) {
        return Result.ok(paidOrderNoticeService.pendingPaid(shopId));
    }
}
