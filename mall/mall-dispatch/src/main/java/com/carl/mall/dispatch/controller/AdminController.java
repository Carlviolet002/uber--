package com.carl.mall.dispatch.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.constant.RoleBitmask;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.result.Result;
import com.carl.mall.dispatch.batch.BatchDispatcher;
import com.carl.mall.dispatch.client.OrderClient;
import com.carl.mall.dispatch.entity.BatchStat;
import com.carl.mall.dispatch.entity.DispatchRecord;
import com.carl.mall.dispatch.entity.Rider;
import com.carl.mall.dispatch.mapper.BatchStatMapper;
import com.carl.mall.dispatch.mapper.DispatchRecordMapper;
import com.carl.mall.dispatch.service.RiderService;
import com.carl.mall.dispatch.service.StrategyService;
import com.carl.mall.dispatch.vo.PoolOrderVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端接口（全部要求 X-User-Role=ADMIN）
 */
@RestController
@RequestMapping("/dispatch/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DispatchRecordMapper dispatchRecordMapper;
    private final BatchStatMapper batchStatMapper;
    private final BatchDispatcher batchDispatcher;
    private final StrategyService strategyService;
    private final RiderService riderService;
    private final OrderClient orderClient;

    /** 派单记录分页（strategy 非空过滤，createTime 倒序） */
    @GetMapping("/records")
    public Result<Page<DispatchRecord>> records(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                                                @RequestParam(required = false) String strategy,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        checkAdmin(roles);
        LambdaQueryWrapper<DispatchRecord> wrapper = new LambdaQueryWrapper<DispatchRecord>()
                .eq(StringUtils.hasText(strategy), DispatchRecord::getStrategy, strategy)
                .orderByDesc(DispatchRecord::getCreateTime);
        return Result.ok(dispatchRecordMapper.selectPage(new Page<>(page, pageSize), wrapper));
    }

    /** 批次统计分页 */
    @GetMapping("/batches")
    public Result<Page<BatchStat>> batches(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        checkAdmin(roles);
        return Result.ok(batchStatMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<BatchStat>().orderByDesc(BatchStat::getCreateTime)));
    }

    /** 手动触发一次批次派单 */
    @PostMapping("/trigger")
    public Result<Map<String, Object>> trigger(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles) {
        checkAdmin(roles);
        return Result.ok(batchDispatcher.doBatch());
    }

    /** 当前策略 */
    @GetMapping("/config")
    public Result<Map<String, Object>> config(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles) {
        checkAdmin(roles);
        return Result.ok(Map.of("strategy", strategyService.current().name()));
    }

    /** 全部派单配置项（key → value），供前端展示派单模式状态 */
    @GetMapping("/config/all")
    public Result<Map<String, String>> allConfigs(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles) {
        checkAdmin(roles);
        return Result.ok(strategyService.allConfigs());
    }

    /** 抢单池：未指派订单列表（无骑手视角，距离字段为 null） */
    @GetMapping("/pool")
    public Result<List<PoolOrderVO>> pool(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles) {
        checkAdmin(roles);
        List<OrderTask> tasks = orderClient.pool();
        return Result.ok(tasks.stream().map(t -> PoolOrderVO.from(t, null)).toList());
    }

    /** 切换策略 */
    @PutMapping("/config")
    public Result<Void> switchStrategy(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                                       @RequestBody StrategyBody body) {
        checkAdmin(roles);
        strategyService.switchTo(body.getStrategy());
        return Result.ok();
    }

    /** 通用配置项写入（白名单：dispatch.mode / dispatch.assignAfterCook） */
    @PutMapping("/config/item")
    public Result<Void> setConfigItem(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                                      @RequestBody ConfigItemBody body) {
        checkAdmin(roles);
        strategyService.setConfig(body.getKey(), body.getValue());
        return Result.ok();
    }

    /** 骑手分页 */
    @GetMapping("/riders")
    public Result<Page<Rider>> riders(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int pageSize) {
        checkAdmin(roles);
        return Result.ok(riderService.pageRiders(page, pageSize));
    }

    /** 新增骑手 */
    @PostMapping("/rider")
    public Result<Rider> addRider(@RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                                  @RequestBody RiderBody body) {
        checkAdmin(roles);
        return Result.ok(riderService.addRider(body.getName(), body.getPhone(), body.getCapacity()));
    }

    private void checkAdmin(long roles) {
        if (!RoleBitmask.has(roles, RoleBitmask.ADMIN)) {
            throw new BizException(403, "无权限");
        }
    }

    @Data
    public static class StrategyBody {
        private String strategy;
    }

    @Data
    public static class ConfigItemBody {
        private String key;
        private String value;
    }

    @Data
    public static class RiderBody {
        private String name;
        private String phone;
        private Integer capacity;
    }
}
