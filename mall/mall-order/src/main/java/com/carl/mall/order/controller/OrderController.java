package com.carl.mall.order.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.result.Result;
import com.carl.mall.order.dto.CancelDTO;
import com.carl.mall.order.dto.CreateOrderDTO;
import com.carl.mall.order.dto.ReviewDTO;
import com.carl.mall.order.service.OrderService;
import com.carl.mall.order.service.ReviewService;
import com.carl.mall.order.vo.OrderDetailVO;
import com.carl.mall.order.vo.OrderVO;
import com.carl.mall.order.vo.ReviewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 订单接口（用户侧，走网关）
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;
    private final ReviewService reviewService;

    public OrderController(OrderService orderService, ReviewService reviewService) {
        this.orderService = orderService;
        this.reviewService = reviewService;
    }

    /**
     * 创建订单
     */
    @PostMapping
    public Result<Map<String, Object>> create(
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
            @RequestBody @Valid CreateOrderDTO dto) {
        return Result.ok(orderService.create(userId, dto));
    }

    /**
     * 模拟支付
     */
    @PutMapping("/{orderNo}/pay")
    public Result<Void> pay(
            @PathVariable String orderNo,
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
            @RequestHeader(value = CommonConstant.HEADER_USER_ROLES, defaultValue = "1") long roles) {
        orderService.pay(orderNo, userId, roles);
        return Result.ok();
    }

    /**
     * 订单列表
     */
    @GetMapping("/list")
    public Result<Page<OrderVO>> list(
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
            @RequestHeader(value = CommonConstant.HEADER_USER_ROLES, defaultValue = "1") long roles,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer urgeFlag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(orderService.list(userId, roles, status, urgeFlag, page, pageSize));
    }

    /**
     * 订单详情
     */
    @GetMapping("/{orderNo}")
    public Result<OrderDetailVO> detail(
            @PathVariable String orderNo,
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
            @RequestHeader(value = CommonConstant.HEADER_USER_ROLES, defaultValue = "1") long roles) {
        return Result.ok(orderService.detail(orderNo, userId, roles));
    }

    /**
     * 取消订单
     */
    @PutMapping("/{orderNo}/cancel")
    public Result<Void> cancel(
            @PathVariable String orderNo,
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
            @RequestHeader(value = CommonConstant.HEADER_USER_ROLES, defaultValue = "1") long roles,
            @RequestBody(required = false) CancelDTO dto) {
        orderService.cancel(orderNo, userId, roles, dto);
        return Result.ok();
    }

    /**
     * 催单
     */
    @PostMapping("/{orderNo}/urge")
    public Result<Void> urge(
            @PathVariable String orderNo,
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId) {
        orderService.urge(orderNo, userId);
        return Result.ok();
    }

    /**
     * 确认收货
     */
    @PutMapping("/{orderNo}/confirm")
    public Result<Void> confirm(
            @PathVariable String orderNo,
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId) {
        orderService.confirm(orderNo, userId);
        return Result.ok();
    }

    /**
     * 提交评价
     */
    @PostMapping("/{orderNo}/review")
    public Result<Long> review(
            @PathVariable String orderNo,
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
            @RequestBody @Valid ReviewDTO dto) {
        return Result.ok(reviewService.submit(orderNo, userId, dto));
    }

    /**
     * 查询自己对订单的评价（未评价 data=null）
     */
    @GetMapping("/{orderNo}/review")
    public Result<ReviewVO> reviewDetail(
            @PathVariable String orderNo,
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId) {
        return Result.ok(reviewService.detail(orderNo, userId));
    }
}
