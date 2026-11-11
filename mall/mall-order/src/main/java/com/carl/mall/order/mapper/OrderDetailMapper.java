package com.carl.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carl.mall.order.entity.OrderDetail;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单明细 Mapper
 */
public interface OrderDetailMapper extends BaseMapper<OrderDetail> {

    /** 批量插入订单明细，替换逐条 insert 的 N+1 写法 */
    @Insert("<script>INSERT INTO t_order_detail (order_id, dish_id, dish_name, num, unit_price, create_time) " +
            "VALUES " +
            "<foreach collection='list' item='d' separator=','>" +
            "(#{d.orderId}, #{d.dishId}, #{d.dishName}, #{d.num}, #{d.unitPrice}, #{d.createTime})" +
            "</foreach></script>")
    void insertBatch(@Param("list") List<OrderDetail> list);
}
