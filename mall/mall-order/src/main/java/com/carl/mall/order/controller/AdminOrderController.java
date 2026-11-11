package com.carl.mall.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.constant.RoleBitmask;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.result.Result;
import com.carl.mall.order.service.OrderService;
import com.carl.mall.order.service.ReviewService;
import com.carl.mall.order.vo.ReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端订单/评价接口（全部要求 X-User-Role=ADMIN）
 */
@RestController
@RequestMapping("/order/admin")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final ReviewService reviewService;

    private void checkAdmin(long roles) {
        if (!RoleBitmask.has(roles, RoleBitmask.ADMIN)) {
            throw new BizException(403, "无权限");
        }
    }

    /** 数据看板：GMV / 订单数 / 已支付数 / 送达率 / 超时率 / 今日订单数 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles) {
        checkAdmin(roles);
        return Result.ok(orderService.statDashboard());
    }

    /** 跨店评价分页（按店铺/关键词过滤，时间倒序） */
    @GetMapping("/reviews")
    public Result<Page<ReviewVO>> reviews(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                                          @RequestParam(required = false) Long shopId,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        checkAdmin(roles);
        return Result.ok(reviewService.listPage(page, pageSize, shopId, keyword));
    }

    /** 审核评价：auditStatus 1 通过 / 2 下架 */
    @PutMapping("/review/{id}/audit")
    public Result<Void> audit(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                              @PathVariable Long id,
                              @RequestBody Map<String, Integer> body) {
        checkAdmin(roles);
        reviewService.audit(id, body.get("auditStatus"));
        return Result.ok();
    }

    /** 平台回复评价 */
    @PutMapping("/review/{id}/reply")
    public Result<Void> reply(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                              @PathVariable Long id,
                              @RequestBody Map<String, String> body) {
        checkAdmin(roles);
        reviewService.platformReply(id, body.get("content"));
        return Result.ok();
    }
}
