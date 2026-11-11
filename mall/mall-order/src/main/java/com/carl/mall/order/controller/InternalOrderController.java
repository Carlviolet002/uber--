package com.carl.mall.order.controller;

import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.result.Result;
import com.carl.mall.order.dto.AssignDTO;
import com.carl.mall.order.dto.RiderActionDTO;
import com.carl.mall.order.dto.ShopActionDTO;
import com.carl.mall.order.service.OrderService;
import com.carl.mall.order.service.ReviewService;
import com.carl.mall.order.vo.OrderStatusVO;
import com.carl.mall.order.vo.OrderVO;
import com.carl.mall.order.vo.ReviewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单内部接口（供 dispatch / shop / rider 服务直调）
 */
@RestController
@RequestMapping("/internal/order")
public class InternalOrderController {

    private final OrderService orderService;
    private final ReviewService reviewService;

    public InternalOrderController(OrderService orderService, ReviewService reviewService) {
        this.orderService = orderService;
        this.reviewService = reviewService;
    }

    /**
     * 待派订单列表（afterCook=true 仅返回已出餐待派订单）
     */
    @GetMapping("/pending")
    public Result<List<OrderTask>> pending(@RequestParam(defaultValue = "false") boolean afterCook) {
        return Result.ok(orderService.pendingOrders(afterCook));
    }

    /**
     * 派单
     */
    @PutMapping("/{orderNo}/assign")
    public Result<Void> assign(@PathVariable String orderNo, @RequestBody @Valid AssignDTO dto) {
        orderService.assign(orderNo, dto);
        return Result.ok();
    }

    /**
     * 商家接单
     */
    @PutMapping("/{orderNo}/shop-accept")
    public Result<Void> shopAccept(@PathVariable String orderNo,
                                   @RequestBody(required = false) ShopActionDTO dto) {
        orderService.shopAccept(orderNo);
        return Result.ok();
    }

    /**
     * 商家拒单
     */
    @PutMapping("/{orderNo}/shop-reject")
    public Result<Void> shopReject(@PathVariable String orderNo,
                                   @RequestBody(required = false) ShopActionDTO dto) {
        orderService.shopReject(orderNo, dto != null ? dto.getReason() : null);
        return Result.ok();
    }

    /**
     * 骑手确认接单（只记日志）
     */
    @PutMapping("/{orderNo}/rider-accept")
    public Result<Void> riderAccept(@PathVariable String orderNo, @RequestBody @Valid RiderActionDTO dto) {
        orderService.riderAccept(orderNo, dto.getRiderId());
        return Result.ok();
    }

    /**
     * 骑手取餐
     */
    @PutMapping("/{orderNo}/picked")
    public Result<Void> picked(@PathVariable String orderNo, @RequestBody @Valid RiderActionDTO dto) {
        orderService.riderPicked(orderNo, dto.getRiderId());
        return Result.ok();
    }

    /**
     * 骑手送达
     */
    @PutMapping("/{orderNo}/delivered")
    public Result<Void> delivered(@PathVariable String orderNo, @RequestBody @Valid RiderActionDTO dto) {
        orderService.riderDelivered(orderNo, dto.getRiderId());
        return Result.ok();
    }

    /**
     * 出餐完成
     */
    @PutMapping("/{orderNo}/finish-cooking")
    public Result<Void> finishCooking(@PathVariable String orderNo) {
        orderService.finishCooking(orderNo);
        return Result.ok();
    }

    /**
     * 店铺评价列表（按时间倒序）
     */
    @GetMapping("/reviews")
    public Result<List<ReviewVO>> reviews(@RequestParam Long shopId) {
        return Result.ok(reviewService.listByShop(shopId));
    }

    /**
     * 商家回复评价（body 为回复内容文本）
     */
    @PutMapping("/review/{id}/reply")
    public Result<Void> replyReview(@PathVariable Long id,
                                    @RequestParam Long shopId,
                                    @RequestBody(required = false) String content) {
        reviewService.reply(id, shopId, content);
        return Result.ok();
    }

    /**
     * 骑手订单列表
     */
    @GetMapping("/by-rider")
    public Result<List<OrderVO>> byRider(
            @RequestParam Long riderId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(orderService.byRider(riderId, status, page, pageSize));
    }

    /**
     * 商家订单列表
     */
    @GetMapping("/by-shop")
    public Result<List<OrderVO>> byShop(
            @RequestParam Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(orderService.byShop(shopId, status, page, pageSize));
    }

    /**
     * 订单状态查询
     */
    @GetMapping("/{orderNo}/status")
    public Result<OrderStatusVO> statusOf(@PathVariable String orderNo) {
        return Result.ok(orderService.statusOf(orderNo));
    }
}
