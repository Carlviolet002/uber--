package com.carl.mall.dispatch.controller;

import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.constant.RoleBitmask;
import com.carl.mall.common.dto.LoginResult;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.result.Result;
import com.carl.mall.common.util.DistanceUtil;
import com.carl.mall.dispatch.client.OrderClient;
import com.carl.mall.dispatch.client.UserClient;
import com.carl.mall.dispatch.entity.Rider;
import com.carl.mall.dispatch.service.LoadTracker;
import com.carl.mall.dispatch.service.RiderService;
import com.carl.mall.dispatch.vo.PoolOrderVO;
import com.carl.mall.dispatch.vo.RecommendVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 骑手端接口（网关注入 X-User-Id 即骑手 id）
 */
@Slf4j
@RestController
@RequestMapping("/dispatch/rider")
@RequiredArgsConstructor
public class RiderController {

    private final UserClient userClient;
    private final OrderClient orderClient;
    private final RiderService riderService;
    private final LoadTracker loadTracker;
    private final ObjectMapper objectMapper;

    /** 骑手登录（无鉴权）：校验账号密码 + 角色必须是 RIDER */
    @PostMapping("/login")
    public Result<LoginResult> login(@RequestBody LoginBody body) {
        LoginResult result = userClient.login(body.getUsername(), body.getPassword());
        if (result == null) {
            throw new BizException(401, "用户名或密码错误");
        }
        long roles = result.getRoles() == null ? RoleBitmask.of(result.getRole()) : result.getRoles();
        if (!RoleBitmask.has(roles, RoleBitmask.RIDER)) {
            throw new BizException(403, "该账号不是骑手");
        }
        return Result.ok(result);
    }

    /** 骑手上线 */
    @PutMapping("/online")
    public Result<Void> online(@RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId) {
        riderService.online(riderId);
        return Result.ok();
    }

    /** 骑手下线 */
    @PutMapping("/offline")
    public Result<Void> offline(@RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId) {
        riderService.offline(riderId);
        return Result.ok();
    }

    /** 骑手位置上报 */
    @PostMapping("/location")
    public Result<Void> location(@RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId,
                                 @Valid @RequestBody LocationBody body) {
        riderService.location(riderId, body.getLng(), body.getLat());
        return Result.ok();
    }

    /** 骑手订单列表（不传 status，订单服务默认查 4 待取餐 / 5 配送中） */
    @GetMapping("/orders")
    public Result<List<Object>> orders(@RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(orderClient.byRider(riderId, null, page, pageSize));
    }

    /** 抢单池：未指派订单列表（含骑手当前位置到店距离），骑手必须在线 */
    @GetMapping("/pool")
    public Result<List<PoolOrderVO>> pool(@RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId,
                                          @RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles) {
        checkRider(roles);
        return Result.ok(buildPool(riderId));
    }

    /**
     * 抢单：骑手抢占订单并指派给自己。assign 内部校验订单状态（仍在池中才成功），
     * 失败时订单服务的错误消息透传返回。
     */
    @PostMapping("/pool/{orderNo}/grab")
    public Result<Void> grab(@RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId,
                             @RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles,
                             @PathVariable String orderNo) {
        checkRider(roles);
        riderService.requireOnline(riderId);
        orderClient.assign(orderNo, riderId);
        // 抢单成功：本地负载 +1、累计接单 +1（与批次指派成功后的记账一致）
        loadTracker.inc(riderId);
        try {
            riderService.incrAcceptCount(riderId);
        } catch (Exception e) {
            log.warn("[抢单] 更新骑手接单数失败 riderId={}", riderId, e);
        }
        return Result.ok();
    }

    /**
     * 推荐单：已指派在途订单 + 抢单池顺路候选 Top5。骑手必须在线。
     */
    @GetMapping("/recommend")
    public Result<RecommendVO> recommend(@RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId,
                                         @RequestHeader(CommonConstant.HEADER_USER_ROLES) long roles) {
        checkRider(roles);
        riderService.requireOnline(riderId);

        RecommendVO vo = new RecommendVO();
        // 已指派订单：状态 9/4/5/11 由订单服务 by-rider 按契约返回，原样展示
        vo.setMyTasks(orderClient.byRider(riderId, null, 1, 50));

        // 抢单池候选按顺路指数（1/(1+到店距离米)）降序取 Top5
        List<RecommendVO.Candidate> candidates = new ArrayList<>();
        for (PoolOrderVO order : buildPool(riderId)) {
            RecommendVO.Candidate c = new RecommendVO.Candidate();
            c.setOrderNo(order.getOrderNo());
            c.setShopId(order.getShopId());
            double distance = order.getDistanceMeters() == null ? 0 : order.getDistanceMeters();
            c.setDistanceMeters(distance);
            c.setScore(1.0 / (1.0 + distance));
            candidates.add(c);
        }
        candidates.sort(Comparator.comparingDouble(RecommendVO.Candidate::getScore).reversed());
        vo.setCandidates(candidates.size() > 5 ? candidates.subList(0, 5) : candidates);
        return Result.ok(vo);
    }

    /**
     * 查询当前登录骑手的在线状态（供前端工作台回显开关）。
     * 只取网关注入的 X-User-Id，不暴露按 id 查询他人状态的入口。
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status(
            @RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId) {
        return Result.ok(riderService.position(riderId));
    }

    /** 查询骑手当前位置（任何登录用户可查） */
    @GetMapping("/position/{riderId}")
    public Result<Map<String, Object>> position(@PathVariable Long riderId) {
        return Result.ok(riderService.position(riderId));
    }

    /** 骑手确认接单（只透传订单服务，dispatch 侧状态由订单服务驱动） */
    @PutMapping("/order/{orderNo}/accept")
    public Result<Void> accept(@RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId,
                               @PathVariable String orderNo) {
        checkOwner(riderId, orderNo);
        orderClient.riderAccept(orderNo, riderId);
        return Result.ok();
    }

    /** 骑手已取餐（负载不变：单量在上门取货时已计） */
    @PutMapping("/order/{orderNo}/picked")
    public Result<Void> picked(@RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId,
                               @PathVariable String orderNo) {
        checkOwner(riderId, orderNo);
        orderClient.riderPicked(orderNo, riderId);
        return Result.ok();
    }

    /** 骑手已送达（送达成功后释放在途负载） */
    @PutMapping("/order/{orderNo}/delivered")
    public Result<Void> delivered(@RequestHeader(CommonConstant.HEADER_USER_ID) Long riderId,
                                  @PathVariable String orderNo) {
        checkOwner(riderId, orderNo);
        orderClient.riderDelivered(orderNo, riderId);
        loadTracker.dec(riderId);
        return Result.ok();
    }

    /**
     * 校验订单归属：查询该骑手在途订单（4/5），orderNo 必须在其中
     */
    private void checkOwner(Long riderId, String orderNo) {
        List<Object> list = orderClient.byRider(riderId, null, 1, 50);
        boolean mine = false;
        for (Object o : list) {
            try {
                Map<?, ?> map = objectMapper.convertValue(o, Map.class);
                if (orderNo.equals(String.valueOf(map.get("orderNo")))) {
                    mine = true;
                    break;
                }
            } catch (Exception ignored) {
                // 跳过无法解析的条目
            }
        }
        if (!mine) {
            throw new BizException(403, "该订单不属于当前骑手");
        }
    }

    /** 角色校验：抢单池/抢单/推荐单仅 RIDER 可用（位掩码） */
    private void checkRider(long roles) {
        if (!RoleBitmask.has(roles, RoleBitmask.RIDER)) {
            throw new BizException(403, "无权限");
        }
    }

    /**
     * 组装抢单池列表：拉取未指派订单 + 逐单计算骑手当前位置到商家（取货点）距离。
     * 前置校验骑手在线（requireOnline 内部校验，不在线抛 403）。
     */
    private List<PoolOrderVO> buildPool(Long riderId) {
        Rider rider = riderService.requireOnline(riderId);
        double riderLng = rider.getCurrentLng() == null ? RiderService.DEFAULT_LNG : rider.getCurrentLng();
        double riderLat = rider.getCurrentLat() == null ? RiderService.DEFAULT_LAT : rider.getCurrentLat();
        List<OrderTask> tasks = orderClient.pool();
        List<PoolOrderVO> list = new ArrayList<>(tasks.size());
        for (OrderTask task : tasks) {
            double distance = DistanceUtil.haversineMeters(
                    riderLng, riderLat, task.getShopLng(), task.getShopLat());
            list.add(PoolOrderVO.from(task, distance));
        }
        return list;
    }

    @Data
    public static class LoginBody {
        private String username;
        private String password;
    }

    @Data
    public static class LocationBody {
        @NotNull(message = "lng 不能为空")
        private Double lng;
        @NotNull(message = "lat 不能为空")
        private Double lat;
    }
}
