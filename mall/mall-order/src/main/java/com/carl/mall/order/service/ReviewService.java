package com.carl.mall.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carl.mall.common.constant.CommonConstant;
import com.carl.mall.common.constant.OrderStatus;
import com.carl.mall.common.exception.BizException;
import com.carl.mall.common.result.ResultCode;
import com.carl.mall.order.dto.ReviewDTO;
import com.carl.mall.order.entity.Order;
import com.carl.mall.order.entity.Review;
import com.carl.mall.order.mapper.OrderMapper;
import com.carl.mall.order.mapper.ReviewMapper;
import com.carl.mall.order.vo.ReviewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单评价服务
 */
@Slf4j
@Service
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;

    public ReviewService(ReviewMapper reviewMapper, OrderMapper orderMapper) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
    }

    /**
     * 提交评价：状态 6 且未评价，仅订单归属用户，返回评价 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long submit(String orderNo, Long userId, ReviewDTO dto) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.COMPLETED) {
            throw new BizException("订单未完成，不可评价");
        }
        if (order.getReviewed() != null && order.getReviewed() == 1) {
            throw new BizException("订单已评价");
        }
        validateScore(dto.getShopScoreOverall());
        validateScore(dto.getShopScoreTaste());
        validateScore(dto.getShopScorePack());
        // 自提单不派骑手，无需评价骑手；配送单仍要求两项骑手评分 1~5
        boolean pickup = CommonConstant.DELIVERY_TYPE_PICKUP.equals(order.getDeliveryType());
        if (!pickup) {
            validateScore(dto.getRiderScoreService());
            validateScore(dto.getRiderScoreSpeed());
        }

        LocalDateTime now = LocalDateTime.now();
        Review review = new Review();
        review.setOrderId(order.getId());
        review.setOrderNo(orderNo);
        review.setUserId(userId);
        review.setShopId(order.getShopId());
        review.setRiderId(order.getRiderId());
        review.setShopScoreOverall(dto.getShopScoreOverall());
        review.setShopScoreTaste(dto.getShopScoreTaste());
        review.setShopScorePack(dto.getShopScorePack());
        // 骑手评分列 NOT NULL 且无默认值，自提单显式写 0 表示「不适用」
        review.setRiderScoreService(pickup ? 0 : dto.getRiderScoreService());
        review.setRiderScoreSpeed(pickup ? 0 : dto.getRiderScoreSpeed());
        review.setContent(dto.getContent());
        review.setImages(dto.getImages());
        review.setAnonymous(dto.getAnonymous() != null && dto.getAnonymous() == 1 ? 1 : 0);
        // 先发后审：显式置 1，不依赖 DDL 默认值
        review.setAuditStatus(1);
        review.setCreateTime(now);
        reviewMapper.insert(review);

        order.setReviewed(1);
        order.setUpdateTime(now);
        orderMapper.updateById(order);
        return review.getId();
    }

    /**
     * 查询自己对某订单的评价（未评价返回 null）
     */
    public ReviewVO detail(String orderNo, Long userId) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权限");
        }
        // 已被平台下架的评价对 C 端不可见（返回 null，前端按「不展示」处理）
        Review review = reviewMapper.selectOne(
                visibleFilter().eq(Review::getOrderId, order.getId()));
        return review == null ? null : toVO(review);
    }

    /**
     * 店铺评价列表（按时间倒序）
     */
    public List<ReviewVO> listByShop(Long shopId) {
        // 已下架评价不再出现在商家端评价列表（admin 的 listPage 不加此过滤）
        List<Review> reviews = reviewMapper.selectList(
                visibleFilter()
                        .eq(Review::getShopId, shopId)
                        .orderByDesc(Review::getCreateTime));
        return reviews.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 商家回复评价
     */
    public void reply(Long reviewId, Long shopId, String content) {
        if (content == null || content.isBlank()) {
            throw new BizException("回复内容不能为空");
        }
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BizException("评价不存在");
        }
        if (!review.getShopId().equals(shopId)) {
            throw new BizException(ResultCode.FORBIDDEN, "非本店评价");
        }
        review.setShopReply(content);
        review.setReplyBy("SHOP");
        review.setReplyTime(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    /**
     * 平台视角：跨店评价分页（按店铺/关键词过滤，时间倒序）
     */
    public Page<ReviewVO> listPage(int page, int pageSize, Long shopId, String keyword) {
        LambdaQueryWrapper<Review> qw = new LambdaQueryWrapper<>();
        if (shopId != null) {
            qw.eq(Review::getShopId, shopId);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Review::getContent, keyword);
        }
        qw.orderByDesc(Review::getCreateTime);
        Page<Review> pageResult = reviewMapper.selectPage(new Page<>(page, pageSize), qw);
        Page<ReviewVO> voPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        voPage.setRecords(pageResult.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 平台审核评价：auditStatus 1 通过 / 2 下架
     */
    public void audit(Long id, Integer auditStatus) {
        if (auditStatus == null || (auditStatus != 1 && auditStatus != 2)) {
            throw new BizException("审核状态非法");
        }
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BizException("评价不存在");
        }
        review.setAuditStatus(auditStatus);
        review.setAuditTime(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    /**
     * 平台回复评价（区别于商家回复）
     */
    public void platformReply(Long id, String content) {
        if (content == null || content.isBlank()) {
            throw new BizException("回复内容不能为空");
        }
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BizException("评价不存在");
        }
        review.setPlatformReply(content);
        review.setReplyBy("PLATFORM");
        review.setReplyTime(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    /**
     * C 端可见过滤：仅排除「已下架」(audit_status=2)。
     * 历史行可能为 NULL，SQL 中 NULL <> 2 结果为 NULL 会被误排除，故显式放行 isNull。
     * admin 的 listPage 不调用本方法（平台必须能看到并处置被下架评价）。
     */
    private LambdaQueryWrapper<Review> visibleFilter() {
        return new LambdaQueryWrapper<Review>()
                .and(w -> w.isNull(Review::getAuditStatus).or().ne(Review::getAuditStatus, 2));
    }

    private void validateScore(Integer score) {
        if (score == null || score < 1 || score > 5) {
            throw new BizException("评分必须为 1~5");
        }
    }

    private ReviewVO toVO(Review review) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setOrderNo(review.getOrderNo());
        vo.setUserId(review.getUserId());
        // mock 模式无法查询 user 服务昵称，置空
        vo.setUserNickname("");
        vo.setShopId(review.getShopId());
        vo.setRiderId(review.getRiderId());
        vo.setShopScoreOverall(review.getShopScoreOverall());
        vo.setShopScoreTaste(review.getShopScoreTaste());
        vo.setShopScorePack(review.getShopScorePack());
        vo.setRiderScoreService(review.getRiderScoreService());
        vo.setRiderScoreSpeed(review.getRiderScoreSpeed());
        vo.setContent(review.getContent());
        vo.setImages(review.getImages());
        vo.setAnonymous(review.getAnonymous());
        vo.setShopReply(review.getShopReply());
        vo.setReplyTime(review.getReplyTime());
        vo.setCreateTime(review.getCreateTime());
        vo.setAuditStatus(review.getAuditStatus());
        vo.setAuditTime(review.getAuditTime());
        vo.setPlatformReply(review.getPlatformReply());
        vo.setReplyBy(review.getReplyBy());
        return vo;
    }
}
