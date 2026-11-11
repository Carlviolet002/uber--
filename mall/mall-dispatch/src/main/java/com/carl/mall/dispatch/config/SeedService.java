package com.carl.mall.dispatch.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carl.mall.dispatch.entity.DispatchConfig;
import com.carl.mall.dispatch.entity.Rider;
import com.carl.mall.dispatch.mapper.DispatchConfigMapper;
import com.carl.mall.dispatch.mapper.RiderMapper;
import com.carl.mall.dispatch.service.StrategyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 种子数据播种服务（mock / real 两模式共用，逻辑与改造前 DataInitializer 一致）：
 * - t_rider 空时插入 3 名骑手（id=4/5/6 与 user 服务种子账号 rider1(id=4) 对应——
 *   骑手账号在 t_user，运营数据在 t_rider，通过 id 关联）
 * - t_dispatch_config 无记录时插入默认值：dispatch.strategy=B、
 *   dispatch.mode=auto、dispatch.assignAfterCook=false
 */
@Slf4j
@Service
public class SeedService {

    private final RiderMapper riderMapper;
    private final DispatchConfigMapper configMapper;

    public SeedService(RiderMapper riderMapper, DispatchConfigMapper configMapper) {
        this.riderMapper = riderMapper;
        this.configMapper = configMapper;
    }

    public void seed() {
        if (riderMapper.selectCount(null) == 0) {
            insertRider(4L, "张三", "13800000011", 113.660, 34.762);
            insertRider(5L, "李四", "13800000012", 113.657, 34.765);
            insertRider(6L, "王五", "13800000013", 113.663, 34.760);
            log.info("[SeedService] 已插入骑手 id=4/5/6");
        }
        insertConfigIfAbsent(StrategyService.CONFIG_KEY, "B");
        insertConfigIfAbsent(StrategyService.CONFIG_KEY_MODE, "auto");
        insertConfigIfAbsent(StrategyService.CONFIG_KEY_ASSIGN_AFTER_COOK, "false");
    }

    private void insertConfigIfAbsent(String key, String value) {
        Long count = configMapper.selectCount(
                new LambdaQueryWrapper<DispatchConfig>().eq(DispatchConfig::getCfgKey, key));
        if (count == 0) {
            DispatchConfig cfg = new DispatchConfig();
            cfg.setCfgKey(key);
            cfg.setCfgValue(value);
            cfg.setCreateTime(LocalDateTime.now());
            cfg.setUpdateTime(LocalDateTime.now());
            configMapper.insert(cfg);
            log.info("[SeedService] 已插入默认配置 {}={}", key, value);
        }
    }

    private void insertRider(Long id, String name, String phone, double lng, double lat) {
        Rider rider = new Rider();
        rider.setId(id);
        rider.setName(name);
        rider.setPhone(phone);
        rider.setStatus(0); // 初始休息，由骑手 App 上线
        rider.setCurrentLng(lng);
        rider.setCurrentLat(lat);
        rider.setCapacity(3);
        rider.setAcceptCount(0);
        rider.setDeleted(0);
        rider.setCreateTime(LocalDateTime.now());
        rider.setUpdateTime(LocalDateTime.now());
        riderMapper.insert(rider);
    }
}
