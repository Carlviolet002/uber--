CREATE DATABASE IF NOT EXISTS mall_order DEFAULT CHARACTER SET utf8mb4;
USE mall_order;

CREATE TABLE t_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  order_no VARCHAR(32) NOT NULL UNIQUE COMMENT '订单号（雪花算法生成）',
  uniq_token VARCHAR(64) UNIQUE COMMENT '幂等令牌（客户端 idempotentKey），唯一约束兜底防重复下单',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  shop_id BIGINT NOT NULL COMMENT '商家ID',
  address_lng DOUBLE NOT NULL COMMENT '送达地址经度（快照）',
  address_lat DOUBLE NOT NULL COMMENT '送达地址纬度（快照）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1待支付/2待接单/3派单中/4待取餐/5配送中/6已完成/7已取消/8已退款/9备餐中/11已送达待确认',
  total DECIMAL(10,2) NOT NULL COMMENT '订单总额',
  pay_amount DECIMAL(10,2) NOT NULL COMMENT '实付金额',
  delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '配送费',
  delivery_type VARCHAR(16) NOT NULL DEFAULT 'DELIVERY' COMMENT '配送方式 DELIVERY=外卖配送 PICKUP=到店自提',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  pay_at DATETIME COMMENT '支付时间',
  accept_at DATETIME COMMENT '商家接单时间',
  assign_at DATETIME COMMENT '派单时间',
  pickup_at DATETIME COMMENT '骑手取餐时间',
  deliver_at DATETIME COMMENT '送达时间',
  cancel_at DATETIME COMMENT '取消时间',
  rider_id BIGINT COMMENT '骑手ID',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  cook_time DATETIME COMMENT '出餐完成时间',
  delivered_time DATETIME COMMENT '送达时间（待用户确认收货）',
  confirm_time DATETIME COMMENT '用户确认收货时间',
  urge_count INT NOT NULL DEFAULT 0 COMMENT '催单次数',
  urge_last_time DATETIME COMMENT '最近一次催单时间',
  remark VARCHAR(255) COMMENT '订单备注',
  tableware INT NOT NULL DEFAULT 1 COMMENT '餐具份数',
  expect_time VARCHAR(50) COMMENT '期望送达时间',
  reviewed TINYINT NOT NULL DEFAULT 0 COMMENT '是否已评价：0未评价/1已评价',
  INDEX idx_user_id (user_id),
  INDEX idx_shop_id (shop_id),
  INDEX idx_rider_id (rider_id),
  INDEX idx_status (status),
  INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE t_order_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  dish_id BIGINT NOT NULL COMMENT '菜品ID',
  dish_name VARCHAR(64) NOT NULL COMMENT '菜品名称（快照）',
  num INT NOT NULL COMMENT '数量',
  unit_price DECIMAL(10,2) NOT NULL COMMENT '单价（快照）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

CREATE TABLE t_order_status_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  from_status TINYINT COMMENT '原状态',
  to_status TINYINT NOT NULL COMMENT '新状态',
  operator_type VARCHAR(16) NOT NULL COMMENT '操作者类型：USER/SHOP/RIDER/SYSTEM',
  operator_id BIGINT COMMENT '操作者ID',
  time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  INDEX idx_order_id (order_id),
  INDEX idx_time (time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态日志表';

CREATE TABLE t_payment_mock (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  order_no VARCHAR(32) NOT NULL COMMENT '订单号',
  trade_no VARCHAR(64) NOT NULL COMMENT '模拟交易号',
  pay_amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
  callback_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回调时间',
  INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟支付流水表';

CREATE TABLE t_review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  order_no VARCHAR(32) NOT NULL COMMENT '订单号',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  shop_id BIGINT NOT NULL COMMENT '商家ID',
  rider_id BIGINT COMMENT '骑手ID',
  shop_score_overall INT NOT NULL COMMENT '商家综合评分（1~5）',
  shop_score_taste INT NOT NULL COMMENT '口味评分（1~5）',
  shop_score_pack INT NOT NULL COMMENT '包装评分（1~5）',
  rider_score_service INT NOT NULL COMMENT '骑手服务评分（1~5）',
  rider_score_speed INT NOT NULL COMMENT '骑手配送速度评分（1~5）',
  content VARCHAR(500) COMMENT '评价内容',
  images VARCHAR(500) COMMENT '图片（逗号分隔 URL）',
  anonymous TINYINT NOT NULL DEFAULT 0 COMMENT '是否匿名：0否/1是',
  shop_reply VARCHAR(500) COMMENT '商家回复',
  reply_time DATETIME COMMENT '回复时间',
  audit_status TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态 1通过 2下架',
  audit_time DATETIME DEFAULT NULL COMMENT '审核时间',
  platform_reply VARCHAR(512) DEFAULT NULL COMMENT '平台回复',
  reply_by VARCHAR(16) DEFAULT NULL COMMENT '回复来源 SHOP/PLATFORM',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  INDEX idx_order_id (order_id),
  INDEX idx_shop_id (shop_id),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单评价表';

-- ============ 种子数据 ============
-- 本库无种子数据

-- ============ OP-3 Outbox 事务消息表 ============
CREATE TABLE IF NOT EXISTS t_outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型=MQ routingKey',
  payload TEXT NOT NULL COMMENT '事件载荷(JSON)',
  delay_minutes BIGINT DEFAULT 0 COMMENT '延迟投递分钟数(0=立即)',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待投递/1已投递/2死信',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  sent_time DATETIME NULL,
  KEY idx_outbox_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Outbox 事务消息表';
