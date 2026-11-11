package com.carl.mall.shop.controller;

import com.carl.mall.common.result.Result;
import com.carl.mall.shop.service.ShopService;
import com.carl.mall.shop.vo.DishVO;
import com.carl.mall.shop.vo.ShopVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家服务内部接口（供订单服务等微服务直连调用，不经过网关）
 */
@RestController
@RequestMapping("/internal/shop")
@RequiredArgsConstructor
public class InternalShopController {

    private final ShopService shopService;

    /** 商家详情 */
    @GetMapping("/{id}")
    public Result<ShopVO> detail(@PathVariable Long id) {
        return Result.ok(shopService.detail(id));
    }

    /** 按 id 列表查询起售菜品（如 dishIds=1,2,3） */
    @GetMapping("/{shopId}/dishes")
    public Result<List<DishVO>> dishes(@PathVariable Long shopId,
                                       @RequestParam List<Long> dishIds) {
        return Result.ok(shopService.dishesByIds(shopId, dishIds));
    }
}
