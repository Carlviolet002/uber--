package com.carl.mall.shop.controller;

import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.constant.RoleBitmask;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.result.Result;
import com.carl.mall.shop.dto.DishDTO;
import com.carl.mall.shop.dto.RejectDTO;
import com.carl.mall.shop.dto.ReplyDTO;
import com.carl.mall.shop.dto.ShopStatusDTO;
import com.carl.mall.shop.service.DishService;
import com.carl.mall.shop.service.ShopOrderProxyService;
import com.carl.mall.shop.service.ShopService;
import com.carl.mall.shop.vo.CategoryVO;
import com.carl.mall.shop.vo.DishVO;
import com.carl.mall.shop.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家服务对外接口（网关 StripPrefix=1 剥掉 /api，网关鉴权）
 */
@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final DishService dishService;
    private final ShopOrderProxyService orderProxyService;

    /** 商家列表（可传经纬度按距离排序） */
    @GetMapping("/list")
    public Result<List<ShopVO>> list(@RequestParam(required = false) Double lng,
                                     @RequestParam(required = false) Double lat) {
        return Result.ok(shopService.list(lng, lat));
    }

    /** 商家详情 */
    @GetMapping("/{id}")
    public Result<ShopVO> detail(@PathVariable Long id) {
        return Result.ok(shopService.detail(id));
    }

    /** 我的店铺（按当前登录账号 ownerUserId 查，不存在则懒建档，修复「商家不存在」） */
    @GetMapping("/mine")
    public Result<ShopVO> mine(@RequestHeader(CommonConstant.HEADER_USER_ID) Long userId) {
        return Result.ok(shopService.mine(userId));
    }

    /** 商家菜品列表 */
    @GetMapping("/{id}/dishes")
    public Result<List<DishVO>> dishes(@PathVariable Long id) {
        return Result.ok(shopService.dishes(id));
    }

    /** 商家分类列表 */
    @GetMapping("/{id}/categories")
    public Result<List<CategoryVO>> categories(@PathVariable Long id) {
        return Result.ok(shopService.categories(id));
    }

    /** 修改营业状态（SHOP/ADMIN） */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Validated @RequestBody ShopStatusDTO dto) {
        shopService.updateStatus(id, dto.getStatus());
        return Result.ok();
    }

    /** 新增菜品（SHOP/ADMIN） */
    @PostMapping("/dish")
    public Result<DishVO> createDish(@Validated @RequestBody DishDTO dto) {
        return Result.ok(dishService.create(dto));
    }

    /** 修改菜品（SHOP/ADMIN） */
    @PutMapping("/dish/{id}")
    public Result<DishVO> updateDish(@PathVariable Long id, @Validated @RequestBody DishDTO dto) {
        return Result.ok(dishService.update(id, dto));
    }

    /** 删除菜品（SHOP/ADMIN） */
    @DeleteMapping("/dish/{id}")
    public Result<Void> deleteDish(@PathVariable Long id) {
        dishService.delete(id);
        return Result.ok();
    }

    /** 切换菜品沽清状态（仅 SHOP） */
    @PutMapping("/dish/{id}/sold-out")
    public Result<DishVO> soldOut(@PathVariable Long id,
                                  @RequestParam boolean soldOut,
                                  @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
                                  @RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles) {
        checkShop(roles);
        Long shopId = shopService.mine(userId).getId();
        return Result.ok(dishService.soldOut(id, shopId, soldOut));
    }

    /** 商家接单（SHOP/ADMIN，代理订单服务） */
    @PutMapping("/order/{orderNo}/accept")
    public Result<Void> acceptOrder(@PathVariable String orderNo) {
        orderProxyService.accept(orderNo);
        return Result.ok();
    }

    /** 商家拒单（SHOP/ADMIN，代理订单服务） */
    @PutMapping("/order/{orderNo}/reject")
    public Result<Void> rejectOrder(@PathVariable String orderNo,
                                    @RequestBody(required = false) RejectDTO dto) {
        orderProxyService.reject(orderNo, dto == null ? null : dto.getReason());
        return Result.ok();
    }

    /** 商家出餐完成（仅 SHOP，代理订单服务） */
    @PutMapping("/order/{orderNo}/finish-cooking")
    public Result<Void> finishCooking(@PathVariable String orderNo,
                                      @RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles) {
        checkShop(roles);
        orderProxyService.finishCooking(orderNo);
        return Result.ok();
    }

    /** 商家订单列表（仅 SHOP，代理订单服务；shopId 缺省按 ownerUserId 解析真实店铺 id） */
    @GetMapping("/orders")
    public Result<Object> orders(@RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
                                 @RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                                 @RequestParam(required = false) Long shopId,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int pageSize) {
        checkShop(roles);
        Long targetShopId = shopId != null ? shopId : shopService.mine(userId).getId();
        return orderProxyService.ordersByShop(targetShopId, status, page, pageSize);
    }

    /** 商家评价列表（公开，登录即可，代理订单服务） */
    @GetMapping("/{shopId}/reviews")
    public Result<Object> reviews(@PathVariable Long shopId) {
        return orderProxyService.reviewsByShop(shopId);
    }

    /** 商家回复评价（仅 SHOP，代理订单服务） */
    @PutMapping("/review/{id}/reply")
    public Result<Void> replyReview(@PathVariable Long id,
                                    @RequestHeader(CommonConstant.HEADER_USER_ID) Long userId,
                                    @RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                                    @Validated @RequestBody ReplyDTO dto) {
        checkShop(roles);
        Long shopId = shopService.mine(userId).getId();
        orderProxyService.replyReview(id, shopId, dto.getContent());
        return Result.ok();
    }

    /** 角色校验：仅 SHOP 可操作（位掩码） */
    private void checkShop(long roles) {
        if (!RoleBitmask.has(roles, RoleBitmask.SHOP)) {
            throw new BizException(403, "仅商家可操作");
        }
    }
}
