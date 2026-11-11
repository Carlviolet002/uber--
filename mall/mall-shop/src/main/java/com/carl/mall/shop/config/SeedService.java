package com.carl.mall.shop.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carl.mall.shop.entity.Category;
import com.carl.mall.shop.entity.Dish;
import com.carl.mall.shop.entity.Shop;
import com.carl.mall.shop.mapper.CategoryMapper;
import com.carl.mall.shop.mapper.DishMapper;
import com.carl.mall.shop.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 种子数据播种服务（mock / real 两模式共用，逻辑与改造前 DataInitializer 一致）：
 * 3 家商家 + 每店 2 个分类 + 共 9 个菜品。
 *
 * <p>owner_user_id（D6-C）：商家消息投递需要 shop→账号 映射。
 * 演示环境仅有 1 个商家账号（shop1，t_user id=3），3 家店全部挂到该账号。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedService {

    /** 商家演示账号：t_user 中 shop1（id=3），作为全部店铺的 owner */
    private static final long OWNER_USER_ID = 3L;

    private final ShopMapper shopMapper;
    private final CategoryMapper categoryMapper;
    private final DishMapper dishMapper;

    public void seed() {
        if (shopMapper.selectCount(null) == 0) {
            insertShop("张姐麻辣烫", "郑州大学主校区商业街 12 号", 113.658, 34.763, "0371-66660001");
            insertShop("老王盖浇饭", "郑州大学主校区东门美食广场", 113.661, 34.758, "0371-66660002");
            insertShop("校园汉堡店", "郑州大学主校区西门 5 号", 113.655, 34.766, "0371-66660003");
            log.info("[SeedService] 商家初始化完成：张姐麻辣烫、老王盖浇饭、校园汉堡店（status=1，owner=shop1）");
        }
        List<Shop> shops = shopMapper.selectList(null);
        if (categoryMapper.selectCount(null) == 0) {
            for (Shop shop : shops) {
                insertCategory(shop.getId(), "热销", 1);
                insertCategory(shop.getId(), "主食", 2);
            }
            log.info("[SeedService] 分类初始化完成：每店 2 个分类");
        }
        if (dishMapper.selectCount(null) == 0) {
            for (Shop shop : shops) {
                List<Category> categories = categoryMapper.selectList(
                        new LambdaQueryWrapper<Category>().eq(Category::getShopId, shop.getId())
                                .orderByAsc(Category::getSort));
                Category hot = categories.get(0);
                Category staple = categories.get(1);
                if (shop.getName().contains("麻辣烫")) {
                    insertDish(shop.getId(), hot.getId(), "招牌麻辣烫", "8.80");
                    insertDish(shop.getId(), hot.getId(), "麻辣香锅", "16.80");
                    insertDish(shop.getId(), staple.getId(), "红薯宽粉", "2.00");
                } else if (shop.getName().contains("盖浇饭")) {
                    insertDish(shop.getId(), hot.getId(), "番茄鸡蛋盖浇饭", "12.00");
                    insertDish(shop.getId(), hot.getId(), "鱼香肉丝盖浇饭", "15.00");
                    insertDish(shop.getId(), staple.getId(), "紫菜蛋花汤", "3.00");
                } else {
                    insertDish(shop.getId(), hot.getId(), "香辣鸡腿堡", "13.90");
                    insertDish(shop.getId(), hot.getId(), "双层牛肉堡", "19.00");
                    insertDish(shop.getId(), staple.getId(), "黄金薯条", "6.00");
                }
            }
            log.info("[SeedService] 菜品初始化完成：共 9 个菜品（价格 2~19 元，status=1）");
        }
    }

    private void insertShop(String name, String address, double lng, double lat, String phone) {
        Shop shop = new Shop();
        shop.setName(name);
        shop.setAddress(address);
        shop.setLng(lng);
        shop.setLat(lat);
        shop.setPhone(phone);
        shop.setStatus(1);
        shop.setOwnerUserId(OWNER_USER_ID);
        shopMapper.insert(shop);
    }

    private void insertCategory(Long shopId, String name, int sort) {
        Category category = new Category();
        category.setShopId(shopId);
        category.setName(name);
        category.setSort(sort);
        categoryMapper.insert(category);
    }

    private void insertDish(Long shopId, Long categoryId, String name, String price) {
        Dish dish = new Dish();
        dish.setShopId(shopId);
        dish.setCategoryId(categoryId);
        dish.setName(name);
        dish.setPrice(new BigDecimal(price));
        dish.setStatus(1);
        dishMapper.insert(dish);
    }
}
