package com.carl.mall.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.dispatch.entity.DispatchConfig;
import com.carl.mall.dispatch.mapper.DispatchConfigMapper;
import com.carl.mall.dispatch.strategy.DispatchStrategy;
import com.carl.mall.dispatch.strategy.GeoNearestStrategy;
import com.carl.mall.dispatch.strategy.HungarianBatchStrategy;
import com.carl.mall.dispatch.strategy.NearestStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 派单策略管理：读取当前生效策略（配置表优先，缺省用 yml），支持运行期切换
 */
@Slf4j
@Component
public class StrategyService {

    public static final String CONFIG_KEY = "dispatch.strategy";
    /** 派单模式：auto 自动派单 / manual 抢单模式 */
    public static final String CONFIG_KEY_MODE = "dispatch.mode";
    /** 是否商家出餐后才进入待派池（true 时拉单接口带 afterCook=true） */
    public static final String CONFIG_KEY_ASSIGN_AFTER_COOK = "dispatch.assignAfterCook";
    private static final Set<String> VALID = Set.of("A", "B", "C");

    private final DispatchConfigMapper configMapper;
    private final NearestStrategy nearestStrategy;
    private final GeoNearestStrategy geoNearestStrategy;
    private final HungarianBatchStrategy hungarianBatchStrategy;

    /** 配置表无记录时的默认策略 */
    @Value("${mall.dispatch.strategy:B}")
    private String defaultStrategy;

    public StrategyService(DispatchConfigMapper configMapper,
                           NearestStrategy nearestStrategy,
                           GeoNearestStrategy geoNearestStrategy,
                           HungarianBatchStrategy hungarianBatchStrategy) {
        this.configMapper = configMapper;
        this.nearestStrategy = nearestStrategy;
        this.geoNearestStrategy = geoNearestStrategy;
        this.hungarianBatchStrategy = hungarianBatchStrategy;
    }

    /** 当前生效策略：t_dispatch_config 优先，缺省回退 yml 配置 */
    public DispatchStrategy current() {
        DispatchConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<DispatchConfig>()
                .eq(DispatchConfig::getCfgKey, CONFIG_KEY)
                .last("limit 1"));
        String name = cfg == null ? defaultStrategy : cfg.getCfgValue();
        return resolve(name);
    }

    /**
     * 读取配置项值（每次实时查库，不做缓存——保证 admin 修改配置后 5s 内批次生效）
     *
     * @param key          配置键，如 dispatch.mode
     * @param defaultValue 配置表无记录时的缺省值
     */
    public String getConfig(String key, String defaultValue) {
        DispatchConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<DispatchConfig>()
                .eq(DispatchConfig::getCfgKey, key)
                .last("limit 1"));
        String value = cfg == null ? null : cfg.getCfgValue();
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /** 是否抢单模式（dispatch.mode=manual） */
    public boolean isManualMode() {
        return "manual".equalsIgnoreCase(getConfig(CONFIG_KEY_MODE, "auto"));
    }

    /** 是否商家出餐后才派单（dispatch.assignAfterCook=true） */
    public boolean assignAfterCook() {
        return "true".equalsIgnoreCase(getConfig(CONFIG_KEY_ASSIGN_AFTER_COOK, "false"));
    }

    /** 全部配置项（key → value），供管理端展示派单模式状态 */
    public Map<String, String> allConfigs() {
        List<DispatchConfig> list = configMapper.selectList(null);
        Map<String, String> map = new LinkedHashMap<>();
        for (DispatchConfig cfg : list) {
            if (cfg.getCfgKey() != null) {
                map.put(cfg.getCfgKey(), cfg.getCfgValue() == null ? "" : cfg.getCfgValue());
            }
        }
        map.putIfAbsent(CONFIG_KEY, defaultStrategy);
        map.putIfAbsent(CONFIG_KEY_MODE, "auto");
        map.putIfAbsent(CONFIG_KEY_ASSIGN_AFTER_COOK, "false");
        return map;
    }

    private DispatchStrategy resolve(String name) {
        return switch (name == null ? "" : name.trim().toUpperCase()) {
            case "A" -> nearestStrategy;
            case "B" -> geoNearestStrategy;
            case "C" -> hungarianBatchStrategy;
            default -> throw new BizException("未知派单策略：" + name + "（可选 A/B/C）");
        };
    }

    /** 切换策略（A/B/C），upsert 到配置表 */
    public void switchTo(String strategy) {
        String name = strategy == null ? "" : strategy.trim().toUpperCase();
        if (!VALID.contains(name)) {
            throw new BizException("非法策略值：" + strategy + "（可选 A/B/C）");
        }
        DispatchConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<DispatchConfig>()
                .eq(DispatchConfig::getCfgKey, CONFIG_KEY)
                .last("limit 1"));
        if (cfg == null) {
            cfg = new DispatchConfig();
            cfg.setCfgKey(CONFIG_KEY);
            cfg.setCfgValue(name);
            cfg.setCreateTime(LocalDateTime.now());
            cfg.setUpdateTime(LocalDateTime.now());
            configMapper.insert(cfg);
        } else {
            cfg.setCfgValue(name);
            cfg.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(cfg);
        }
        log.info("派单策略已切换为: {}", name);
    }

    /**
     * 通用配置项写入（upsert），仅允许白名单 key：
     * dispatch.mode（auto/manual）、dispatch.assignAfterCook（true/false）
     */
    public void setConfig(String key, String value) {
        if (key == null || !(CONFIG_KEY_MODE.equals(key) || CONFIG_KEY_ASSIGN_AFTER_COOK.equals(key))) {
            throw new BizException("不支持的配置项：" + key);
        }
        String v = value == null ? "" : value.trim();
        if (CONFIG_KEY_MODE.equals(key) && !"auto".equalsIgnoreCase(v) && !"manual".equalsIgnoreCase(v)) {
            throw new BizException("dispatch.mode 仅支持 auto / manual");
        }
        if (CONFIG_KEY_ASSIGN_AFTER_COOK.equals(key) && !"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
            throw new BizException("dispatch.assignAfterCook 仅支持 true / false");
        }
        String normalized = v.toLowerCase();
        DispatchConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<DispatchConfig>()
                .eq(DispatchConfig::getCfgKey, key)
                .last("limit 1"));
        if (cfg == null) {
            cfg = new DispatchConfig();
            cfg.setCfgKey(key);
            cfg.setCfgValue(normalized);
            cfg.setCreateTime(LocalDateTime.now());
            cfg.setUpdateTime(LocalDateTime.now());
            configMapper.insert(cfg);
        } else {
            cfg.setCfgValue(normalized);
            cfg.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(cfg);
        }
        log.info("派单配置已更新: {} = {}", key, normalized);
    }
}
