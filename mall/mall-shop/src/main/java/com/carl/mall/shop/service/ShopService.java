package com.carl.mall.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carl.mall.common.cache.CacheService;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.util.DistanceUtil;
import com.carl.mall.shop.entity.Category;
import com.carl.mall.shop.entity.Dish;
import com.carl.mall.shop.entity.Shop;
import com.carl.mall.shop.mapper.CategoryMapper;
import com.carl.mall.shop.mapper.DishMapper;
import com.carl.mall.shop.mapper.ShopMapper;
import com.carl.mall.shop.vo.CategoryVO;
import com.carl.mall.shop.vo.DishVO;
import com.carl.mall.shop.vo.ShopVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商家服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopService {

    private static final String DISH_LIST_CACHE_PREFIX = "dish:list:";
    private static final long DISH_LIST_CACHE_TTL_SECONDS = 300;

    private final ShopMapper shopMapper;
    private final CategoryMapper categoryMapper;
    private final DishMapper dishMapper;

    /** 进程内锁：按 ownerUserId 隔离，避免并发懒建档双插（单实例并发竞态防护） */
    private final ConcurrentHashMap<Long, Object> shopLocks = new ConcurrentHashMap<>();
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    /**
     * 营业中商家列表，传 lng/lat 时计算距离并按距离排序
     */
    public List<ShopVO> list(Double lng, Double lat) {
        List<Shop> shops = shopMapper.selectList(
                new LambdaQueryWrapper<Shop>().eq(Shop::getStatus, 1).orderByAsc(Shop::getId));
        List<ShopVO> vos = shops.stream().map(this::toShopVO).toList();
        if (lng != null && lat != null) {
            for (ShopVO vo : vos) {
                vo.setDistanceMeters(DistanceUtil.haversineMeters(lng, lat, vo.getLng(), vo.getLat()));
            }
            vos = vos.stream()
                    .sorted(Comparator.comparing(ShopVO::getDistanceMeters))
                    .toList();
        }
        return vos;
    }

    /**
     * 商家详情
     */
    public ShopVO detail(Long id) {
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            throw new BizException(404, "商家不存在");
        }
        return toShopVO(shop);
    }

    /**
     * 确保商家档案存在：按 ownerUserId 查，无则懒建档。
     * 用于修复「门户注册 SHOP 后进入商家端报 商家不存在」的根因（后端未自动建档）。
     */
    public ShopVO ensureShop(Long ownerUserId) {
        // 进程内锁：避免并发请求同时查到 null 导致双插（单实例下的并发竞态防护；
        // 多实例部署建议进一步在 t_shop.owner_user_id 上加唯一约束）。
        synchronized (shopLocks.computeIfAbsent(ownerUserId, k -> new Object())) {
            // 演示环境一个商家账号可拥有多家店（SeedService 把 3 家种子店挂到同一 owner），
            // 故按 id 升序取首店，避免 selectOne 因返回多行抛 TooManyResultsException。
            List<Shop> shops = shopMapper.selectList(
                    new LambdaQueryWrapper<Shop>()
                            .eq(Shop::getOwnerUserId, ownerUserId)
                            .orderByAsc(Shop::getId));
            Shop shop = shops.isEmpty() ? null : shops.get(0);
            if (shop == null) {
                shop = new Shop();
                shop.setName("我的店铺" + ownerUserId);
                // 懒建档：t_shop 的 address/lng/lat 为 NOT NULL，必须给默认值，否则 insert 报 500
                shop.setAddress("待完善");
                shop.setLng(0.0);
                shop.setLat(0.0);
                shop.setStatus(0);
                shop.setOwnerUserId(ownerUserId);
                shop.setCreateTime(LocalDateTime.now());
                shop.setUpdateTime(LocalDateTime.now());
                shopMapper.insert(shop);
                log.info("[ShopService] 为 ownerUserId={} 自动建档店铺 id={}", ownerUserId, shop.getId());
            }
            return toShopVO(shop);
        }
    }

    /**
     * 我的店铺（按 ownerUserId），不存在时懒建档后返回。
     */
    public ShopVO mine(Long ownerUserId) {
        return ensureShop(ownerUserId);
    }

    /**
     * 修改营业状态（删状态缓存）
     */
    public void updateStatus(Long id, Integer status) {
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            throw new BizException(404, "商家不存在");
        }
        Shop update = new Shop();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        shopMapper.updateById(update);
        cacheService.delete("shop:status:" + id);
    }

    /**
     * 商家分类列表
     */
    public List<CategoryVO> categories(Long shopId) {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .eq(Category::getShopId, shopId)
                        .orderByAsc(Category::getSort)
                        .orderByAsc(Category::getId))
                .stream().map(this::toCategoryVO).toList();
    }

    /**
     * 商家菜品列表（带缓存，TTL 300s）
     */
    public List<DishVO> dishes(Long shopId) {
        String cacheKey = DISH_LIST_CACHE_PREFIX + shopId;
        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<DishVO>>() {
                });
            } catch (JsonProcessingException e) {
                log.warn("[ShopService] 菜品缓存反序列化失败，回源数据库：{}", e.getMessage());
            }
        }
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getShopId, shopId)
                .orderByAsc(Dish::getCategoryId)
                .orderByAsc(Dish::getId));
        List<DishVO> vos = dishes.stream().map(this::toDishVO).toList();
        try {
            cacheService.set(cacheKey, objectMapper.writeValueAsString(vos), DISH_LIST_CACHE_TTL_SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("[ShopService] 菜品缓存序列化失败：{}", e.getMessage());
        }
        return vos;
    }

    /**
     * 按 id 列表查询起售菜品（供订单服务内部调用，沽清菜品不返回）
     */
    public List<DishVO> dishesByIds(Long shopId, List<Long> dishIds) {
        if (dishIds == null || dishIds.isEmpty()) {
            return List.of();
        }
        return dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getShopId, shopId)
                        .in(Dish::getId, dishIds)
                        .eq(Dish::getStatus, 1)
                        .eq(Dish::getSoldOut, 0))
                .stream().map(this::toDishVO).toList();
    }

    private ShopVO toShopVO(Shop shop) {
        ShopVO vo = new ShopVO();
        vo.setId(shop.getId());
        vo.setName(shop.getName());
        vo.setAddress(shop.getAddress());
        vo.setLng(shop.getLng());
        vo.setLat(shop.getLat());
        vo.setPhone(shop.getPhone());
        vo.setStatus(shop.getStatus());
        return vo;
    }

    private CategoryVO toCategoryVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setShopId(category.getShopId());
        vo.setName(category.getName());
        vo.setSort(category.getSort());
        return vo;
    }

    private DishVO toDishVO(Dish dish) {
        DishVO vo = new DishVO();
        vo.setId(dish.getId());
        vo.setShopId(dish.getShopId());
        vo.setCategoryId(dish.getCategoryId());
        vo.setName(dish.getName());
        vo.setPrice(dish.getPrice());
        vo.setImage(dish.getImage());
        vo.setStatus(dish.getStatus());
        vo.setSoldOut(dish.getSoldOut());
        return vo;
    }
}
