package com.carl.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟支付流水
 */
@Data
@TableName("t_payment_mock")
public class PaymentMock {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String tradeNo;

    private BigDecimal payAmount;

    private LocalDateTime callbackTime;
}
