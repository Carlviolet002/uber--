package com.carl.mall.shop.service;

import com.carl.mall.common.cache.CacheService;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.shop.dto.DishDTO;
import com.carl.mall.shop.entity.Category;
import com.carl.mall.shop.entity.Dish;
import com.carl.mall.shop.mapper.CategoryMapper;
import com.carl.mall.shop.mapper.DishMapper;
import com.carl.mall.shop.vo.DishVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 菜品服务
 */
@Service
@RequiredArgsConstructor
public class DishService {

    private final DishMapper dishMapper;
    private final CategoryMapper categoryMapper;
    private final CacheService cacheService;

    /**
     * 新增菜品（校验分类归属商家，成功后删缓存）
     */
    public DishVO create(DishDTO dto) {
        Category category = categoryMapper.selectById(dto.getCategoryId());
        if (category == null || !category.getShopId().equals(dto.getShopId())) {
            throw new BizException("分类不属于该商家");
        }
        Dish dish = new Dish();
        dish.setShopId(dto.getShopId());
        dish.setCategoryId(dto.getCategoryId());
        dish.setName(dto.getName());
        dish.setPrice(dto.getPrice());
        dish.setImage(dto.getImage());
        dish.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        dishMapper.insert(dish);
        deleteDishCache(dto.getShopId());
        return toVO(dish);
    }

    /**
     * 修改菜品（改分类时校验归属，成功后删缓存）
     */
    public DishVO update(Long id, DishDTO dto) {
        Dish exist = dishMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "菜品不存在");
        }
        if (dto.getCategoryId() != null) {
            Category category = categoryMapper.selectById(dto.getCategoryId());
            if (category == null || !category.getShopId().equals(exist.getShopId())) {
                throw new BizException("分类不属于该商家");
            }
        }
        Dish update = new Dish();
        update.setId(id);
        update.setCategoryId(dto.getCategoryId());
        update.setName(dto.getName());
        update.setPrice(dto.getPrice());
        update.setImage(dto.getImage());
        update.setStatus(dto.getStatus());
        update.setUpdateTime(LocalDateTime.now());
        dishMapper.updateById(update);
        deleteDishCache(exist.getShopId());
        return toVO(dishMapper.selectById(id));
    }

    /**
     * 删除菜品（逻辑删除，删缓存）
     */
    public void delete(Long id) {
        Dish exist = dishMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "菜品不存在");
        }
        dishMapper.deleteById(id);
        deleteDishCache(exist.getShopId());
    }

    /**
     * 切换菜品沽清状态（校验菜品属于本店，成功后删缓存）
     */
    public DishVO soldOut(Long id, Long shopId, boolean soldOut) {
        Dish exist = dishMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "菜品不存在");
        }
        if (!exist.getShopId().equals(shopId)) {
            throw new BizException(403, "菜品不属于本店");
        }
        Dish update = new Dish();
        update.setId(id);
        update.setSoldOut(soldOut ? 1 : 0);
        update.setUpdateTime(LocalDateTime.now());
        dishMapper.updateById(update);
        deleteDishCache(exist.getShopId());
        return toVO(dishMapper.selectById(id));
    }

    private void deleteDishCache(Long shopId) {
        cacheService.delete("dish:list:" + shopId);
    }

    private DishVO toVO(Dish dish) {
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
