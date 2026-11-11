package com.carl.mall.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.dto.RiderState;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.geo.GeoIndex;
import com.carl.mall.dispatch.entity.Rider;
import com.carl.mall.dispatch.mapper.RiderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 骑手运营服务：上下线、位置上报、骑手快照查询
 */
@Slf4j
@Component
public class RiderService {

    /** 未上报位置时的默认坐标（郑州市中心附近，与种子数据一致） */
    public static final double DEFAULT_LNG = 113.66;
    public static final double DEFAULT_LAT = 34.76;

    private final RiderMapper riderMapper;
    private final GeoIndex geoIndex;
    private final LoadTracker loadTracker;

    /** 进程内锁：按 riderId 隔离，避免并发懒建档双插（单实例并发竞态防护） */
    private final ConcurrentHashMap<Long, Object> riderLocks = new ConcurrentHashMap<>();

    public RiderService(RiderMapper riderMapper, GeoIndex geoIndex, LoadTracker loadTracker) {
        this.riderMapper = riderMapper;
        this.geoIndex = geoIndex;
        this.loadTracker = loadTracker;
    }

    /** 骑手上线：状态置 1，位置写入 GEO 索引 */
    public void online(Long riderId) {
        ensureRider(riderId);
        Rider rider = getById(riderId);
        double lng = rider.getCurrentLng() == null ? DEFAULT_LNG : rider.getCurrentLng();
        double lat = rider.getCurrentLat() == null ? DEFAULT_LAT : rider.getCurrentLat();
        rider.setCurrentLng(lng);
        rider.setCurrentLat(lat);
        rider.setStatus(1);
        rider.setUpdateTime(LocalDateTime.now());
        riderMapper.updateById(rider);
        geoIndex.add(CommonConstant.GEO_RIDER_ONLINE, lng, lat, String.valueOf(riderId));
        log.info("骑手 {} 上线: ({},{})", riderId, lng, lat);
    }

    /** 骑手下线：状态置 0，从 GEO 索引移除 */
    public void offline(Long riderId) {
        ensureRider(riderId);
        Rider rider = getById(riderId);
        rider.setStatus(0);
        rider.setUpdateTime(LocalDateTime.now());
        riderMapper.updateById(rider);
        geoIndex.remove(CommonConstant.GEO_RIDER_ONLINE, String.valueOf(riderId));
        log.info("骑手 {} 下线", riderId);
    }

    /**
     * 骑手位置上报：仅在线骑手写入 GEO 索引。
     * 避免已下线骑手因位置上报被重新加回派单池，造成 DB 离线但 GEO 在线的状态分裂。
     */
    public void location(Long riderId, Double lng, Double lat) {
        ensureRider(riderId);
        Rider rider = getById(riderId);
        rider.setCurrentLng(lng);
        rider.setCurrentLat(lat);
        rider.setUpdateTime(LocalDateTime.now());
        riderMapper.updateById(rider);
        if (rider.getStatus() != null && rider.getStatus() == 1) {
            geoIndex.add(CommonConstant.GEO_RIDER_ONLINE, lng, lat, String.valueOf(riderId));
        }
    }

    /** 在线骑手快照（派单策略输入），currentLoad 从 LoadTracker 取 */
    public List<RiderState> onlineRiders() {
        LambdaQueryWrapper<Rider> wrapper = new LambdaQueryWrapper<Rider>()
                .eq(Rider::getStatus, 1)
                .eq(Rider::getDeleted, 0);
        List<Rider> riders = riderMapper.selectList(wrapper);
        List<RiderState> states = new ArrayList<>(riders.size());
        for (Rider r : riders) {
            RiderState s = new RiderState();
            s.setRiderId(r.getId());
            s.setName(r.getName());
            s.setLng(r.getCurrentLng() == null ? DEFAULT_LNG : r.getCurrentLng());
            s.setLat(r.getCurrentLat() == null ? DEFAULT_LAT : r.getCurrentLat());
            s.setCapacity(r.getCapacity() == null ? 3 : r.getCapacity());
            s.setCurrentLoad(loadTracker.get(r.getId()));
            s.setAcceptCount(r.getAcceptCount() == null ? 0 : r.getAcceptCount());
            states.add(s);
        }
        return states;
    }

    public Page<Rider> pageRiders(long page, long pageSize) {
        return riderMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<Rider>().orderByAsc(Rider::getId));
    }

    public Rider addRider(String name, String phone, Integer capacity) {
        Rider rider = new Rider();
        rider.setName(name);
        rider.setPhone(phone);
        rider.setStatus(0);
        rider.setCapacity(capacity == null ? 3 : capacity);
        rider.setAcceptCount(0);
        rider.setDeleted(0);
        rider.setCreateTime(LocalDateTime.now());
        rider.setUpdateTime(LocalDateTime.now());
        riderMapper.insert(rider);
        return rider;
    }

    /**
     * 确保骑手运营档案存在：自助注册的骑手仅有 t_user（role=RIDER），尚无 t_rider。
     * 首次上线/上报位置/抢单等操作时自动建档，并将 t_rider.id 与用户 id 对齐
     * （设计约定 t_rider.id == t_user.id），避免「骑手不存在」报错。
     */
    private void ensureRider(Long riderId) {
        // 进程内锁：避免并发操作同时查到 null 导致双插（单实例下的并发竞态防护；
        // 多实例部署建议进一步在 t_rider.id 上加唯一约束）。
        synchronized (riderLocks.computeIfAbsent(riderId, k -> new Object())) {
            if (riderMapper.selectById(riderId) != null) {
                return;
            }
            Rider rider = new Rider();
            rider.setId(riderId);
            rider.setName("骑手" + riderId);
            // phone 列 NOT NULL 且无默认值，自动建档必须显式置值，否则 insert 抛 500
            rider.setPhone("");
            rider.setStatus(0);
            rider.setCapacity(3);
            rider.setAcceptCount(0);
            rider.setDeleted(0);
            rider.setCreateTime(LocalDateTime.now());
            rider.setUpdateTime(LocalDateTime.now());
            riderMapper.insert(rider);
            log.info("自动为骑手 {} 建档 t_rider", riderId);
        }
    }

    public Rider getById(Long riderId) {
        Rider rider = riderMapper.selectById(riderId);
        if (rider == null) {
            throw new BizException(404, "骑手不存在，请联系管理员添加");
        }
        return rider;
    }

    /**
     * 校验骑手在线（status=1，与 online() 的上线逻辑一致），不在线抛 403。
     * 抢单池/抢单/推荐单等骑手端功能前置校验。
     */
    public Rider requireOnline(Long riderId) {
        ensureRider(riderId);
        Rider rider = getById(riderId);
        if (rider.getStatus() == null || rider.getStatus() != 1) {
            throw new BizException(403, "骑手不在线，请先上线");
        }
        return rider;
    }

    /** 累计接单数 +1（BatchDispatcher 指派成功后调用） */
    public void incrAcceptCount(Long riderId) {
        ensureRider(riderId);
        Rider rider = getById(riderId);
        rider.setAcceptCount((rider.getAcceptCount() == null ? 0 : rider.getAcceptCount()) + 1);
        rider.setUpdateTime(LocalDateTime.now());
        riderMapper.updateById(rider);
    }

    /** 骑手当前位置（供订单服务/地图展示查询） */
    public Map<String, Object> position(Long riderId) {
        ensureRider(riderId);
        Rider rider = getById(riderId);
        Map<String, Object> map = new HashMap<>();
        map.put("riderId", rider.getId());
        map.put("name", rider.getName());
        map.put("lng", rider.getCurrentLng());
        map.put("lat", rider.getCurrentLat());
        map.put("status", rider.getStatus());
        return map;
    }
}
