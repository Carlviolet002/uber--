package com.carl.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carl.mall.common.cache.CacheService;
import com.carl.mall.common.discovery.ServiceEndpointResolver;
import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.constant.OrderStatus;
import com.carl.mall.common.constant.RoleBitmask;
import com.carl.mall.common.dto.OrderTask;
import com.carl.mall.common.event.EventPublisher;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.result.Result;
import com.carl.mall.common.result.ResultCode;
import com.carl.mall.order.dto.AssignDTO;
import com.carl.mall.order.dto.CancelDTO;
import com.carl.mall.order.dto.CreateOrderDTO;
import com.carl.mall.order.entity.Order;
import com.carl.mall.order.entity.OrderDetail;
import com.carl.mall.order.entity.OrderStatusLog;
import com.carl.mall.order.entity.PaymentMock;
import com.carl.mall.order.mapper.OrderDetailMapper;
import com.carl.mall.order.mapper.OrderMapper;
import com.carl.mall.order.mapper.OrderStatusLogMapper;
import com.carl.mall.order.mapper.PaymentMockMapper;
import com.carl.mall.order.vo.OrderDetailVO;
import com.carl.mall.order.vo.OrderItemVO;
import com.carl.mall.order.vo.OrderStatusVO;
import com.carl.mall.order.vo.OrderVO;
import com.carl.mall.order.ws.OrderWsHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.dao.DuplicateKeyException;

import com.carl.mall.common.resilience.ResilienceTemplate;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 订单服务（核心逻辑）
 */
@Slf4j
@Service
public class OrderService {

    private static final int DEFAULT_PROMISE_MINUTES = 30;
    private static final int PENDING_LIMIT = 50;
    private static final long IDEMPOTENT_TTL_SECONDS = 3600L;
    // OP-7 读路径缓存 TTL（秒）：地址较稳定给长一点，菜品价格可能变动给短一点
    private static final long ADDR_CACHE_TTL_SECONDS = 600L;
    private static final long SHOP_CACHE_TTL_SECONDS = 300L;
    private static final long DISH_CACHE_TTL_SECONDS = 120L;

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final PaymentMockMapper paymentMockMapper;
    private final OrderStateMachine orderStateMachine;
    private final CacheService cacheService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${mall.services.user-url:http://localhost:8081}")
    private String userUrl;

    @Value("${mall.services.shop-url:http://localhost:8082}")
    private String shopUrl;

    @Value("${mall.order.delivery-fee:5.00}")
    private BigDecimal deliveryFee;

    @Value("${mall.order.pay-timeout-minutes:15}")
    private int payTimeoutMinutes;

    /** D6-B：true 时超时关单走 RabbitMQ 延迟队列，false 走 30s 扫描器（mock 默认 false） */
    @Value("${mall.order.mq-timeout:false}")
    private boolean mqTimeoutEnabled;

    /** OP-1：服务端点解析（默认 Nacos，失败回退直连 mall.services.*-url） */
    private final ServiceEndpointResolver resolver;
    /** 共享连接/读超时请求工厂 */
    private final SimpleClientHttpRequestFactory httpFactory = newHttpFactory();

    private static SimpleClientHttpRequestFactory newHttpFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(1000);
        f.setReadTimeout(2000);
        return f;
    }

    /** 自注入代理，用于在本类内调用 @Transactional 方法以确保事务生效；@Lazy 打破自引用循环依赖 */
    @Autowired
    @Lazy
    private OrderService self;

    /** 共享韧性模板（熔断 + 退避重试），来自 mall-common */
    private final ResilienceTemplate resilienceTemplate;

    /** OP-3 Outbox：订单创建/超时事件与订单同事务落库，由投递器异步发送 */
    private final OutboxService outboxService;

    public OrderService(OrderMapper orderMapper,
                        OrderDetailMapper orderDetailMapper,
                        OrderStatusLogMapper orderStatusLogMapper,
                        PaymentMockMapper paymentMockMapper,
                        OrderStateMachine orderStateMachine,
                        CacheService cacheService,
                        EventPublisher eventPublisher,
                        ObjectMapper objectMapper,
                        ResilienceTemplate resilienceTemplate,
                        ServiceEndpointResolver resolver,
                        OutboxService outboxService) {
        this.orderMapper = orderMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.orderStatusLogMapper = orderStatusLogMapper;
        this.paymentMockMapper = paymentMockMapper;
        this.orderStateMachine = orderStateMachine;
        this.cacheService = cacheService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.resilienceTemplate = resilienceTemplate;
        this.resolver = resolver;
        this.outboxService = outboxService;
    }

    /** OP-1：user 服务客户端基址（Nacos 优先，失败回退 userUrl）。 */
    private RestClient userClient() {
        return RestClient.builder()
                .baseUrl(resolver.resolve("mall-user", userUrl))
                .requestFactory(httpFactory)
                .build();
    }

    /** OP-1：shop 服务客户端基址（Nacos 优先，失败回退 shopUrl）。 */
    private RestClient shopClient() {
        return RestClient.builder()
                .baseUrl(resolver.resolve("mall-shop", shopUrl))
                .requestFactory(httpFactory)
                .build();
    }

    /** 配送方式归一化：空值/非法值一律按 DELIVERY，兼容 smoke 等未传该字段的旧调用方 */
    private String deliveryTypeOf(CreateOrderDTO dto) {
        String type = dto == null ? null : dto.getDeliveryType();
        return CommonConstant.DELIVERY_TYPE_PICKUP.equals(type)
                ? CommonConstant.DELIVERY_TYPE_PICKUP
                : CommonConstant.DELIVERY_TYPE_DELIVERY;
    }

    private boolean isPickup(CreateOrderDTO dto) {
        return CommonConstant.DELIVERY_TYPE_PICKUP.equals(deliveryTypeOf(dto));
    }

    private boolean isPickup(Order order) {
        return CommonConstant.DELIVERY_TYPE_PICKUP.equals(order.getDeliveryType());
    }

    // ==================== 用户侧 ====================

    /**
     * 创建订单（三段式）
     * ① 幂等前置（事务外）② 远程校验/重算（事务外，带超时）③ 独立事务落库 ④ 提交后发事件
     */
    public Map<String, Object> create(Long userId, CreateOrderDTO dto) {
        // 1. 幂等：已存在则直接返回（必须在远程调用之前，避免重复浪费）
        String idempotentKey = "idempotent:" + dto.getIdempotentKey();
        String cached = cacheService.get(idempotentKey);
        if (cached != null && !cached.isBlank()) {
            Order existing = getByOrderNo(cached);
            BigDecimal cachedTotal = existing != null && existing.getTotal() != null
                    ? existing.getTotal() : BigDecimal.ZERO;
            return Map.of("orderNo", cached, "total", cachedTotal);
        }

        // 2. 收货地址（事务外，RestClient 已带超时）
        //    到店自提无需地址：先置 0，取到商家坐标后再用店铺位置兜底（t_order.address_lng/lat 为 NOT NULL）
        boolean pickup = isPickup(dto);
        double addressLng = 0;
        double addressLat = 0;
        if (!pickup) {
            Map<String, Object> address = fetchAddress(dto.getAddressId());
            Object lngObj = address == null ? null : address.get("lng");
            Object latObj = address == null ? null : address.get("lat");
            if (lngObj == null || latObj == null) {
                throw new BizException("收货地址不存在");
            }
            addressLng = toDouble(lngObj);
            addressLat = toDouble(latObj);
        }

        // 3. 商家（status 必须 1 营业中）
        Map<String, Object> shop = fetchShop(dto.getShopId());
        if (shop == null || shop.isEmpty()) {
            throw new BizException("商家不存在");
        }
        if (toInt(shop.get("status")) != 1) {
            throw new BizException("商家已打烊");
        }
        double shopLng = toDouble(shop.get("lng"));
        double shopLat = toDouble(shop.get("lat"));
        if (pickup) {
            // 自提单用店铺坐标兜底，保证 NOT NULL 列有值且语义为「到店位置」
            addressLng = shopLng;
            addressLat = shopLat;
        }

        // 4. 菜品校验 + 服务端重算金额 + 构建明细快照
        String dishIds = dto.getItems().stream()
                .map(item -> String.valueOf(item.getDishId()))
                .collect(Collectors.joining(","));
        List<Map<String, Object>> dishList = fetchDishes(dto.getShopId(), dishIds);
        Map<Long, Map<String, Object>> dishMap = new HashMap<>();
        for (Map<String, Object> dish : dishList) {
            if (dish == null) {
                continue;
            }
            Object idObj = dish.get("id") != null ? dish.get("id") : dish.get("dishId");
            if (idObj != null) {
                dishMap.put(Long.parseLong(String.valueOf(idObj)), dish);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal itemsTotal = BigDecimal.ZERO;
        List<OrderDetail> details = new ArrayList<>();
        for (CreateOrderDTO.ItemDTO item : dto.getItems()) {
            Map<String, Object> dish = dishMap.get(item.getDishId());
            if (dish == null || toInt(dish.get("status")) != 1) {
                throw new BizException("菜品不存在或已停售");
            }
            BigDecimal price = toBigDecimal(dish.get("price"));
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("菜品不存在或已停售");
            }
            itemsTotal = itemsTotal.add(price.multiply(BigDecimal.valueOf(item.getNum())));
            OrderDetail detail = new OrderDetail();
            Object nameObj = dish.get("dishName") != null ? dish.get("dishName") : dish.get("name");
            detail.setDishId(item.getDishId());
            detail.setDishName(nameObj == null ? "菜品" : String.valueOf(nameObj));
            detail.setNum(item.getNum());
            detail.setUnitPrice(toBigDecimal(dish.get("price")));
            detail.setCreateTime(now);
            details.add(detail);
        }
        // 自提免配送费；外卖沿用配置的 deliveryFee
        BigDecimal fee = pickup ? BigDecimal.ZERO : deliveryFee;
        BigDecimal total = itemsTotal.add(fee);
        BigDecimal payAmount = total;

        // 5. 独立事务落库（远程调用已全部完成，事务只包本地写）。
        //    OP-3：order.created / order.timeout 事件在 persistOrder 内与订单【同一事务】写入 t_outbox，
        //    由 OutboxService 调度投递（至少一次），消除"提交后发 MQ 但崩溃丢事件"的窗口。
        Order saved = self.persistOrder(userId, dto, addressLng, addressLat, shopLng, shopLat,
                total, payAmount, details, now, idempotentKey);

        return Map.of("orderNo", saved.getOrderNo(), "total", total);
    }

    /**
     * 订单落库（独立事务，仅含本地 DB 写）。
     * 通过 self 代理调用以确保 @Transactional 生效（避免同类内自调用绕过代理）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Order persistOrder(Long userId, CreateOrderDTO dto, double addressLng, double addressLat,
                              double shopLng, double shopLat,
                              BigDecimal total, BigDecimal payAmount, List<OrderDetail> details,
                              LocalDateTime now, String idempotentKey) {
        String orderNo = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setShopId(dto.getShopId());
        order.setAddressLng(addressLng);
        order.setAddressLat(addressLat);
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setTotal(total);
        order.setPayAmount(payAmount);
        order.setDeliveryFee(isPickup(dto) ? BigDecimal.ZERO : deliveryFee);
        order.setDeliveryType(deliveryTypeOf(dto));
        order.setCreatedAt(now);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        order.setRemark(dto.getRemark());
        order.setTableware(dto.getTableware() == null ? 0 : dto.getTableware());
        order.setExpectTime(dto.getExpectTime());
        order.setUniqToken(idempotentKey);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 幂等兜底：Redis 过期后用户用同一 idempotentKey 重试时，唯一约束拦截重复插入，
            // 直接返回已存在的订单（与首单结果一致），避免生成重复订单。
            log.warn("[persistOrder] 幂等命中（uniq_token 重复）idempotentKey={}", idempotentKey);
            Order existing = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>().eq(Order::getUniqToken, idempotentKey));
            if (existing != null) {
                return existing;
            }
            throw e;
        }

        long orderId = order.getId();
        for (OrderDetail d : details) {
            d.setOrderId(orderId);
        }
        orderDetailMapper.insertBatch(details);
        writeLog(order.getId(), null, OrderStatus.PENDING_PAY, "USER", userId);
        cacheService.set(idempotentKey, orderNo, IDEMPOTENT_TTL_SECONDS);

        // OP-3 Outbox：与订单同事务写入事件（事务提交即事件必达 outbox，回滚则一并回滚）
        // 自提单不登记 order.created —— 从源头保证它不会进入派单池被指派给骑手
        if (!isPickup(dto)) {
            OrderTask task = new OrderTask();
            task.setOrderId(order.getId());
            task.setOrderNo(orderNo);
            task.setShopId(dto.getShopId());
            task.setShopLng(shopLng);
            task.setShopLat(shopLat);
            task.setAddressLng(addressLng);
            task.setAddressLat(addressLat);
            task.setExpectedMinutes(DEFAULT_PROMISE_MINUTES);
            task.setCreatedAt(System.currentTimeMillis());
            outboxService.enqueue("order.created", task, 0L);
        }
        // D6-B 延迟关单：mqTimeout 开启时登记延迟投递事件，由投递器经 x.order.delay 延迟投递
        if (mqTimeoutEnabled) {
            outboxService.enqueue("order.timeout", orderNo, payTimeoutMinutes);
        }
        return order;
    }

    /**
     * 模拟支付：1 -> 2
     */
    public void pay(String orderNo, Long userId, long roles) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!RoleBitmask.has(roles, RoleBitmask.ADMIN) && !order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PENDING_PAY) {
            throw new BizException("订单状态不允许支付");
        }
        // 模拟支付流水
        PaymentMock payment = new PaymentMock();
        payment.setOrderNo(orderNo);
        payment.setTradeNo("MOCK" + orderNo + System.currentTimeMillis());
        payment.setPayAmount(order.getPayAmount());
        payment.setCallbackTime(LocalDateTime.now());
        paymentMockMapper.insert(payment);

        order.setPayAt(LocalDateTime.now());
        transit(order, OrderStatus.PENDING_ACCEPT, "USER", userId, null);
        // D6-A：payload 补 shopId，供 mall-shop 消费 q.shop.accept 落待接单缓存
        eventPublisher.publish("order.paid", Map.of("orderNo", orderNo, "shopId", order.getShopId()));
        pushWs(order.getUserId(), order);
    }

    /**
     * 取消订单：1/2 -> 7；3/4/5/9/11 -> 8（9/11 覆盖自提单备餐与待取餐阶段）
     */
    public void cancel(String orderNo, Long userId, long roles, CancelDTO dto) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!RoleBitmask.has(roles, RoleBitmask.ADMIN) && !order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限");
        }
        int status = order.getStatus();
        String reason = dto != null ? dto.getReason() : null;
        if (status == OrderStatus.PENDING_PAY || status == OrderStatus.PENDING_ACCEPT) {
            order.setCancelAt(LocalDateTime.now());
            transit(order, OrderStatus.CANCELLED, "USER", userId, null);
        } else if (status == OrderStatus.DISPATCHING || status == OrderStatus.PENDING_PICKUP
                || status == OrderStatus.DELIVERING || status == OrderStatus.COOKING
                || status == OrderStatus.DELIVERED_CONFIRM) {
            order.setCancelAt(LocalDateTime.now());
            transit(order, OrderStatus.REFUNDED, "USER", userId, reason);
        } else {
            throw new BizException("当前状态不可取消");
        }
        publishStatusChanged(order);
        pushWs(order.getUserId(), order);
    }

    /**
     * 确认收货：已支付后的任意非终态 -> 6（仅订单归属用户）。
     * 「随时确认」允许提前完结，具体合法边由 OrderStateMachine 统一约束。
     */
    public void confirm(String orderNo, Long userId) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限");
        }
        int status = order.getStatus() == null ? 0 : order.getStatus();
        if (status == OrderStatus.PENDING_PAY || status == OrderStatus.COMPLETED
                || status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED) {
            throw new BizException("当前状态不可确认收货");
        }
        order.setConfirmTime(LocalDateTime.now());
        transit(order, OrderStatus.COMPLETED, "USER", userId, null);
        pushWs(order.getUserId(), order);
    }

    /**
     * 催单：状态 3/9/4/5 可催，5 分钟内限一次（仅订单归属用户）
     */
    public void urge(String orderNo, Long userId) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限");
        }
        int status = order.getStatus() == null ? -1 : order.getStatus();
        if (status != OrderStatus.DISPATCHING && status != OrderStatus.COOKING
                && status != OrderStatus.PENDING_PICKUP && status != OrderStatus.DELIVERING) {
            throw new BizException("当前状态不可催单");
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getUrgeLastTime() != null && order.getUrgeLastTime().isAfter(now.minusMinutes(5))) {
            throw new BizException(ResultCode.BAD_REQUEST, "5分钟内请勿重复催单");
        }
        order.setUrgeCount((order.getUrgeCount() == null ? 0 : order.getUrgeCount()) + 1);
        order.setUrgeLastTime(now);
        order.setUpdateTime(now);
        orderMapper.updateById(order);

        Map<String, Object> payload = Map.of(
                "orderId", order.getId(),
                "orderNo", order.getOrderNo(),
                "shopId", order.getShopId(),
                "userId", userId,
                "urgeCount", order.getUrgeCount(),
                "time", now.toString());
        eventPublisher.publish("order.urged", payload);
    }

    /**
     * 订单列表：ADMIN 查全部，否则只查自己
     */
    public Page<OrderVO> list(Long userId, long roles, Integer status, Integer urgeFlag, int page, int pageSize) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        if (!RoleBitmask.has(roles, RoleBitmask.ADMIN)) {
            qw.eq(Order::getUserId, userId);
        }
        if (status != null) {
            qw.eq(Order::getStatus, status);
        }
        if (urgeFlag != null && urgeFlag == 1) {
            qw.ge(Order::getUrgeCount, 1);
        }
        qw.orderByDesc(Order::getCreatedAt);
        Page<Order> pageResult = orderMapper.selectPage(new Page<>(page, pageSize), qw);
        Page<OrderVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 订单详情
     */
    public OrderDetailVO detail(String orderNo, Long userId, long roles) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        // 订单详情可见范围：管理员、下单用户本人，以及被指派该单的骑手。
        // 骑手端「我的任务 → 任务详情」复用本接口，需按 riderId 放行且不越权查看他人订单。
        boolean isAdmin = RoleBitmask.has(roles, RoleBitmask.ADMIN);
        boolean isOwner = order.getUserId().equals(userId);
        boolean isAssignedRider = RoleBitmask.has(roles, RoleBitmask.RIDER)
                && order.getRiderId() != null && order.getRiderId().equals(userId);
        if (!isAdmin && !isOwner && !isAssignedRider) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限");
        }
        List<OrderDetail> items = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, order.getId()));
        List<OrderStatusLog> logs = orderStatusLogMapper.selectList(
                new LambdaQueryWrapper<OrderStatusLog>()
                        .eq(OrderStatusLog::getOrderId, order.getId())
                        .orderByAsc(OrderStatusLog::getTime));
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrder(toVO(order));
        vo.setItems(items);
        vo.setLogs(logs);
        return vo;
    }

    // ==================== 内部接口（dispatch / shop / rider） ====================

    /**
     * 待派订单：status=3，按创建时间升序，最多 50 条
     */
    public List<OrderTask> pendingOrders() {
        return pendingOrders(false);
    }

    /**
     * 待派订单：status=3，按创建时间升序，最多 50 条；afterCook=true 时只返回已出餐（cook_time 非空）订单
     */
    public List<OrderTask> pendingOrders(boolean afterCook) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getStatus, OrderStatus.DISPATCHING);
        // 双保险：即使历史数据误把自提单推进派单中，也绝不进入派单池
        qw.eq(Order::getDeliveryType, CommonConstant.DELIVERY_TYPE_DELIVERY);
        if (afterCook) {
            qw.isNotNull(Order::getCookTime);
        }
        qw.orderByAsc(Order::getCreatedAt)
                .last("LIMIT " + PENDING_LIMIT);
        List<Order> orders = orderMapper.selectList(qw);
        List<OrderTask> tasks = new ArrayList<>(orders.size());
        for (Order order : orders) {
            OrderTask task = new OrderTask();
            task.setOrderId(order.getId());
            task.setOrderNo(order.getOrderNo());
            task.setShopId(order.getShopId());
            double lng = 0;
            double lat = 0;
            try {
                Map<String, Object> shop = fetchShop(order.getShopId());
                if (shop != null && !shop.isEmpty()) {
                    lng = toDouble(shop.get("lng"));
                    lat = toDouble(shop.get("lat"));
                } else {
                    log.warn("[pendingOrders] 商家不存在 orderNo={} shopId={}", order.getOrderNo(), order.getShopId());
                }
            } catch (RestClientException e) {
                log.warn("[pendingOrders] 查询商家失败 orderNo={} shopId={} err={}",
                        order.getOrderNo(), order.getShopId(), e.getMessage());
            }
            task.setShopLng(lng);
            task.setShopLat(lat);
            task.setAddressLng(order.getAddressLng());
            task.setAddressLat(order.getAddressLat());
            task.setExpectedMinutes(DEFAULT_PROMISE_MINUTES);
            task.setCreatedAt(order.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * 派单：状态3 且未出餐 -> 9 备餐中；状态3 且已出餐 -> 4 待取餐
     */
    public void assign(String orderNo, AssignDTO dto) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.DISPATCHING) {
            throw new BizException("当前状态不可指派");
        }
        order.setRiderId(dto.getRiderId());
        order.setAssignAt(LocalDateTime.now());
        if (order.getCookTime() == null) {
            // 未出餐：进入备餐中
            transit(order, OrderStatus.COOKING, "SYSTEM", dto.getRiderId(), null);
        } else {
            // 已出餐：直接待取餐
            transit(order, OrderStatus.PENDING_PICKUP, "SYSTEM", dto.getRiderId(), null);
        }
        pushWs(order.getUserId(), order);
    }

    /**
     * 出餐完成：9 -> 4；3 -> 记录出餐时间，状态保持 3（待骑手指派后转 4）
     */
    public void finishCooking(String orderNo) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getStatus() == null) {
            throw new BizException("当前状态不可出餐");
        }
        if (order.getStatus() == OrderStatus.COOKING) {
            order.setCookTime(now);
            // 自提出餐后直接到 11（餐已备好，待到店取/确认）；外卖到 4 待取餐
            int to = isPickup(order) ? OrderStatus.DELIVERED_CONFIRM : OrderStatus.PENDING_PICKUP;
            transit(order, to, "SHOP", order.getShopId(), null);
        } else if (order.getStatus() == OrderStatus.DISPATCHING) {
            order.setCookTime(now);
            order.setUpdateTime(now);
            orderMapper.updateById(order);
            writeLog(order.getId(), OrderStatus.DISPATCHING, OrderStatus.DISPATCHING, "SHOP", order.getShopId());
        } else {
            throw new BizException("当前状态不可出餐");
        }
        publishStatusChanged(order);
        pushWs(order.getUserId(), order);
    }

    /**
     * 商家接单：外卖 2 -> 3 派单中；自提 2 -> 9 备餐中（不进入派单池）
     */
    public void shopAccept(String orderNo) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PENDING_ACCEPT) {
            throw new BizException("订单状态异常，无法接单");
        }
        order.setAcceptAt(LocalDateTime.now());
        // 自提单不进入 3 派单中，直接到 9 备餐中；外卖单维持原流转
        int to = isPickup(order) ? OrderStatus.COOKING : OrderStatus.DISPATCHING;
        transit(order, to, "SHOP", order.getShopId(), null);
        publishStatusChanged(order);
        pushWs(order.getUserId(), order);
    }

    /**
     * 商家拒单：2 -> 8
     */
    public void shopReject(String orderNo, String reason) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PENDING_ACCEPT) {
            throw new BizException("订单状态异常，无法拒单");
        }
        order.setCancelAt(LocalDateTime.now());
        transit(order, OrderStatus.REFUNDED, "SHOP", order.getShopId(), reason);
        publishStatusChanged(order);
        pushWs(order.getUserId(), order);
    }

    /**
     * 骑手确认接单：只记日志，不改状态
     */
    public void riderAccept(String orderNo, Long riderId) {
        Order order = checkRider(orderNo, riderId);
        writeLog(order.getId(), order.getStatus(), order.getStatus(), "RIDER", riderId);
    }

    /**
     * 骑手取餐：4 -> 5
     */
    public void riderPicked(String orderNo, Long riderId) {
        Order order = checkRider(orderNo, riderId);
        order.setPickupAt(LocalDateTime.now());
        transit(order, OrderStatus.DELIVERING, "RIDER", riderId, null);
        publishStatusChanged(order);
        pushWs(order.getUserId(), order);
    }

    /**
     * 骑手送达：5 -> 11 已送达待确认
     */
    public void riderDelivered(String orderNo, Long riderId) {
        Order order = checkRider(orderNo, riderId);
        LocalDateTime now = LocalDateTime.now();
        order.setDeliverAt(now);
        order.setDeliveredTime(now);
        transit(order, OrderStatus.DELIVERED_CONFIRM, "RIDER", riderId, null);
        publishStatusChanged(order);
        pushWs(order.getUserId(), order);
    }

    /**
     * 骑手订单列表：status 缺省查 9/4/5/11（含已指派未出餐的备餐中订单）
     */
    public List<OrderVO> byRider(Long riderId, Integer status, int page, int pageSize) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getRiderId, riderId);
        if (status != null) {
            qw.eq(Order::getStatus, status);
        } else {
            qw.in(Order::getStatus, OrderStatus.COOKING, OrderStatus.PENDING_PICKUP,
                    OrderStatus.DELIVERING, OrderStatus.DELIVERED_CONFIRM);
        }
        qw.orderByDesc(Order::getCreatedAt);
        return orderMapper.selectPage(new Page<>(page, pageSize), qw)
                .getRecords().stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 商家订单列表
     */
    public List<OrderVO> byShop(Long shopId, Integer status, int page, int pageSize) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getShopId, shopId);
        if (status != null) {
            qw.eq(Order::getStatus, status);
        }
        qw.orderByDesc(Order::getCreatedAt);
        return orderMapper.selectPage(new Page<>(page, pageSize), qw)
                .getRecords().stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 订单状态轻量查询
     */
    public OrderStatusVO statusOf(String orderNo) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        OrderStatusVO vo = new OrderStatusVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(order.getStatus());
        vo.setStatusName(OrderStatus.name(order.getStatus()));
        return vo;
    }

    // ==================== 内部封装 ====================

    /**
     * 统一状态更新：assertCanTransit + update + 写日志
     */
    private void transit(Order order, int to, String operatorType, Long operatorId, String remark) {
        int from = order.getStatus();
        orderStateMachine.assertCanTransit(from, to);
        order.setStatus(to);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        writeLog(order.getId(), from, to, operatorType, operatorId);
        if (remark != null && !remark.isBlank()) {
            log.warn("[订单状态流转] orderNo={} {} -> {} remark={}", order.getOrderNo(), from, to, remark);
        }
    }

    private void writeLog(Long orderId, Integer fromStatus, Integer toStatus, String operatorType, Long operatorId) {
        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(orderId);
        statusLog.setFromStatus(fromStatus);
        statusLog.setToStatus(toStatus);
        statusLog.setOperatorType(operatorType);
        statusLog.setOperatorId(operatorId);
        statusLog.setTime(LocalDateTime.now());
        orderStatusLogMapper.insert(statusLog);
    }

    /**
     * WS 推送订单状态变更
     */
    private void pushWs(Long userId, Order order) {
        Map<String, Object> payload = Map.of(
                "type", "ORDER_STATUS",
                "orderNo", order.getOrderNo(),
                "status", order.getStatus(),
                "statusName", OrderStatus.name(order.getStatus()),
                "time", LocalDateTime.now().toString()
        );
        OrderWsHandler.pushToUser(userId, payload);
    }

    /**
     * 发布订单状态变更事件（D6-C）：商家站内消息消费 order.status.changed 生成 t_message。
     * mock 模式 MockEventPublisher 仅打日志，无副作用。
     */
    private void publishStatusChanged(Order order) {
        eventPublisher.publish("order.status.changed", Map.of(
                "orderNo", order.getOrderNo(),
                "shopId", order.getShopId(),
                "status", order.getStatus(),
                "statusName", OrderStatus.name(order.getStatus())
        ));
    }

    private Order getByOrderNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
    }

    private Order checkRider(String orderNo, Long riderId) {
        Order order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (order.getRiderId() == null || !order.getRiderId().equals(riderId)) {
            throw new BizException(ResultCode.FORBIDDEN, "非本单骑手");
        }
        return order;
    }

    private OrderVO toVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setShopId(order.getShopId());
        vo.setStatus(order.getStatus());
        vo.setStatusName(OrderStatus.name(order.getStatus()));
        vo.setTotal(order.getTotal());
        vo.setPayAmount(order.getPayAmount());
        vo.setDeliveryFee(order.getDeliveryFee());
        vo.setDeliveryType(order.getDeliveryType());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setRiderId(order.getRiderId());
        vo.setCookTime(order.getCookTime());
        vo.setUrgeCount(order.getUrgeCount());
        vo.setUrgeLastTime(order.getUrgeLastTime());
        vo.setRemark(order.getRemark());
        vo.setTableware(order.getTableware());
        vo.setExpectTime(order.getExpectTime());
        vo.setReviewed(order.getReviewed());
        // 菜品明细摘要：按订单查快照（演示数据量小，N+1 可接受）
        List<OrderDetail> details = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, order.getId()));
        vo.setItems(details.stream().map(d -> {
            OrderItemVO it = new OrderItemVO();
            it.setDishId(d.getDishId());
            it.setDishName(d.getDishName());
            it.setNum(d.getNum());
            it.setUnitPrice(d.getUnitPrice());
            return it;
        }).collect(Collectors.toList()));
        return vo;
    }

    // ==================== 远程调用 ====================

    /**
     * OP-7 读路径缓存：地址 Cache-Aside。命中直接返回，省一次跨服务调用。
     */
    private Map<String, Object> fetchAddress(Long addressId) {
        String cacheKey = "o:addr:" + addressId;
        Map<String, Object> cached = readCache(cacheKey, new TypeReference<Map<String, Object>>() {
        });
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        Map<String, Object> fresh = fetchWithResilience(() -> {
            Result<Map<String, Object>> result = userClient().get()
                    .uri("/internal/user/address/{addressId}", addressId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Map<String, Object>>>() {
                    });
            return result == null ? null : result.getData();
        }, Map.of(), "fetchAddress", addressId);
        // 仅缓存成功且非空的结果，避免把降级空 Map 污染缓存
        if (fresh != null && !fresh.isEmpty()) {
            writeCache(cacheKey, fresh, ADDR_CACHE_TTL_SECONDS);
        }
        return fresh;
    }

    /**
     * OP-7 读路径缓存：店铺 Cache-Aside（含营业状态，短 TTL 兜底，避免打烊店铺长时间仍可下单）。
     */
    private Map<String, Object> fetchShop(Long shopId) {
        String cacheKey = "o:shop:" + shopId;
        Map<String, Object> cached = readCache(cacheKey, new TypeReference<Map<String, Object>>() {
        });
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        Map<String, Object> fresh = fetchWithResilience(() -> {
            Result<Map<String, Object>> result = shopClient().get()
                    .uri("/internal/shop/{shopId}", shopId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Map<String, Object>>>() {
                    });
            return result == null ? null : result.getData();
        }, Map.of(), "fetchShop", shopId);
        if (fresh != null && !fresh.isEmpty()) {
            writeCache(cacheKey, fresh, SHOP_CACHE_TTL_SECONDS);
        }
        return fresh;
    }

    /**
     * OP-7 读路径缓存：菜品快照 Cache-Aside（key 含 dishIds，价格变动靠短 TTL 兜底）。
     */
    private List<Map<String, Object>> fetchDishes(Long shopId, String dishIds) {
        String cacheKey = "o:dishes:" + shopId + ":" + dishIds;
        List<Map<String, Object>> cached = readCache(cacheKey, new TypeReference<List<Map<String, Object>>>() {
        });
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        List<Map<String, Object>> fresh = fetchWithResilience(() -> {
            Result<List<Map<String, Object>>> result = shopClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/shop/{shopId}/dishes")
                            .queryParam("dishIds", dishIds)
                            .build(shopId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<List<Map<String, Object>>>>() {
                    });
            return result == null || result.getData() == null ? List.of() : result.getData();
        }, List.of(), "fetchDishes", shopId);
        if (fresh != null && !fresh.isEmpty()) {
            writeCache(cacheKey, fresh, DISH_CACHE_TTL_SECONDS);
        }
        return fresh;
    }

    /** 读缓存并反序列化；未命中/解析失败返回 null（回源）。 */
    private <T> T readCache(String key, TypeReference<T> type) {
        try {
            String value = cacheService.get(key);
            if (value != null && !value.isBlank()) {
                return objectMapper.readValue(value, type);
            }
        } catch (Exception e) {
            log.warn("[OP-7 cache] 读缓存失败 key={}：{}", key, e.getMessage());
        }
        return null;
    }

    /** 写缓存（JSON 序列化）；失败仅告警，不影响主流程。 */
    private void writeCache(String key, Object value, long ttlSeconds) {
        try {
            cacheService.set(key, objectMapper.writeValueAsString(value), ttlSeconds);
        } catch (Exception e) {
            log.warn("[OP-7 cache] 写缓存失败 key={}：{}", key, e.getMessage());
        }
    }

    /**
     * 远程调用统一封装：熔断(CircuitBreaker) 包 退避重试(Retry)，失败（含熔断开启 CallNotPermittedException）
     * 统一降级为 fallback，避免下游故障拖垮下单主链路。熔断/重试只识别基础设施异常(RestClientException)，
     * 业务异常(BizException) 不计入、不重试。
     */
    private <T> T fetchWithResilience(Supplier<T> supplier, T fallback, String name, Object id) {
        return resilienceTemplate.run(name, supplier, () -> {
            log.warn("[{}] 服务降级 id={}", name, id);
            return fallback;
        });
    }

    // ==================== 类型转换 ====================

    private int toInt(Object value) {
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private double toDouble(Object value) {
        return value == null ? 0 : Double.parseDouble(String.valueOf(value));
    }

    private BigDecimal toBigDecimal(Object value) {
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    /**
     * 管理端数据看板：GMV / 订单数 / 已支付数 / 送达率 / 超时率 / 今日订单数。
     * 数据量小，直接全量拉取内存聚合（避免复杂 SQL 与跨库 join）。
     */
    public Map<String, Object> statDashboard() {
        List<Order> all = orderMapper.selectList(new LambdaQueryWrapper<Order>());
        BigDecimal gmv = BigDecimal.ZERO;
        int paidCount = 0, completedCount = 0, deliveredCount = 0, timeoutCount = 0, todayCount = 0;
        LocalDate today = LocalDate.now();
        for (Order o : all) {
            if (o.getStatus() != null && o.getStatus() >= OrderStatus.PENDING_ACCEPT) {
                paidCount++;
                gmv = gmv.add(o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO);
            }
            if (o.getStatus() != null && o.getStatus() == OrderStatus.COMPLETED) {
                completedCount++;
            }
            if (o.getDeliveredTime() != null) {
                deliveredCount++;
                long mins = Duration.between(o.getCreatedAt(), o.getDeliveredTime()).toMinutes();
                if (mins > DEFAULT_PROMISE_MINUTES) {
                    timeoutCount++;
                }
            }
            if (o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().equals(today)) {
                todayCount++;
            }
        }
        double deliveredRate = paidCount == 0 ? 0.0 : (double) completedCount / paidCount;
        double timeoutRate = deliveredCount == 0 ? 0.0 : (double) timeoutCount / deliveredCount;
        Map<String, Object> m = new HashMap<>();
        m.put("gmv", gmv);
        m.put("orderCount", all.size());
        m.put("paidCount", paidCount);
        m.put("completedCount", completedCount);
        m.put("deliveredRate", deliveredRate);
        m.put("timeoutRate", timeoutRate);
        m.put("todayOrderCount", todayCount);
        return m;
    }
}
