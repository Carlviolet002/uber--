-- ============================================================
-- mall_db 统一建表脚本（15 张表）
-- 生成：2026-10-22（Mock→真实连接 R1）
-- 来源：合并 mall-user/shop/order/dispatch 四份 schema.sql + t_message（D6-C）
-- 使用：mysql -h127.0.0.1 -umall -p"mall@123" mall_db < mall_db_schema.sql
-- 说明：种子数据由各服务 RealDataInitializer 播种（D2），本脚本仅含
--       t_dispatch_config 的幂等默认配置（与 dispatch schema.sql 保持一致）
-- ============================================================
USE mall_db;

-- ---------- 用户域 ----------
CREATE TABLE IF NOT EXISTS t_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(32) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  nickname VARCHAR(32),
  phone VARCHAR(16),
  role VARCHAR(8) NOT NULL DEFAULT 'USER',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_address (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  receiver_name VARCHAR(32) NOT NULL,
  receiver_phone VARCHAR(16) NOT NULL,
  lng DOUBLE NOT NULL,
  lat DOUBLE NOT NULL,
  detail VARCHAR(255) NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_address_user ON t_address (user_id);

-- ---------- 消息域（D6-C 新增） ----------
CREATE TABLE IF NOT EXISTS t_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  receiver_id BIGINT NOT NULL COMMENT '接收者账号ID（商家账号）',
  type VARCHAR(16) NOT NULL COMMENT '消息类型：ORDER_*（订单事件）',
  title VARCHAR(64) NOT NULL,
  content VARCHAR(255) NOT NULL,
  biz_no VARCHAR(64) COMMENT '关联业务单号（订单号）',
  is_read TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息表';
CREATE INDEX idx_message_receiver ON t_message (receiver_id);

-- ---------- 商家域 ----------
CREATE TABLE IF NOT EXISTS t_shop (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  address VARCHAR(255) NOT NULL,
  lng DOUBLE NOT NULL,
  lat DOUBLE NOT NULL,
  phone VARCHAR(16),
  status TINYINT NOT NULL DEFAULT 0,
  owner_user_id BIGINT COMMENT '归属商家账号ID（D6-C 新增，t_user.id）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_category_shop ON t_category (shop_id);

CREATE TABLE IF NOT EXISTS t_dish (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  shop_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  image VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 0,
  sold_out TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_dish_shop ON t_dish (shop_id);
CREATE INDEX idx_dish_category ON t_dish (category_id);

-- ---------- 订单域 ----------
CREATE TABLE IF NOT EXISTS t_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL UNIQUE,
  uniq_token VARCHAR(64) UNIQUE,
  user_id BIGINT NOT NULL,
  shop_id BIGINT NOT NULL,
  address_lng DOUBLE NOT NULL,
  address_lat DOUBLE NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  total DECIMAL(10,2) NOT NULL,
  pay_amount DECIMAL(10,2) NOT NULL,
  delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  delivery_type VARCHAR(16) NOT NULL DEFAULT 'DELIVERY',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  pay_at DATETIME,
  accept_at DATETIME,
  assign_at DATETIME,
  pickup_at DATETIME,
  deliver_at DATETIME,
  cancel_at DATETIME,
  rider_id BIGINT,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  cook_time DATETIME,
  delivered_time DATETIME,
  confirm_time DATETIME,
  urge_count INT DEFAULT 0,
  urge_last_time DATETIME,
  remark VARCHAR(255),
  tableware INT DEFAULT 1,
  expect_time VARCHAR(50),
  reviewed TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_order_user ON t_order (user_id);
CREATE INDEX idx_order_shop ON t_order (shop_id);
CREATE INDEX idx_order_rider ON t_order (rider_id);
CREATE INDEX idx_order_status ON t_order (status);
CREATE INDEX idx_order_created ON t_order (created_at);

CREATE TABLE IF NOT EXISTS t_order_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  dish_id BIGINT NOT NULL,
  dish_name VARCHAR(64) NOT NULL,
  num INT NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_detail_order ON t_order_detail (order_id);

CREATE TABLE IF NOT EXISTS t_order_status_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  from_status TINYINT,
  to_status TINYINT NOT NULL,
  operator_type VARCHAR(16) NOT NULL,
  operator_id BIGINT,
  time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_log_order ON t_order_status_log (order_id);

CREATE TABLE IF NOT EXISTS t_payment_mock (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL,
  trade_no VARCHAR(64) NOT NULL,
  pay_amount DECIMAL(10,2) NOT NULL,
  callback_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  shop_id BIGINT NOT NULL,
  rider_id BIGINT,
  shop_score_overall INT NOT NULL,
  shop_score_taste INT NOT NULL,
  shop_score_pack INT NOT NULL,
  rider_score_service INT NOT NULL,
  rider_score_speed INT NOT NULL,
  content VARCHAR(500),
  images VARCHAR(500),
  anonymous TINYINT DEFAULT 0,
  shop_reply VARCHAR(500),
  reply_time DATETIME,
  audit_status TINYINT NOT NULL DEFAULT 1,
  audit_time DATETIME DEFAULT NULL,
  platform_reply VARCHAR(512) DEFAULT NULL,
  reply_by VARCHAR(16) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_review_shop ON t_review (shop_id);
CREATE INDEX idx_review_order ON t_review (order_id);

-- ---------- 派单域 ----------
CREATE TABLE IF NOT EXISTS t_rider (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(32) NOT NULL,
  phone VARCHAR(16) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  current_lng DOUBLE,
  current_lat DOUBLE,
  capacity INT NOT NULL DEFAULT 3,
  accept_count INT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_rider_status ON t_rider (status);

CREATE TABLE IF NOT EXISTS t_dispatch_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  batch_no VARCHAR(32) NOT NULL,
  strategy VARCHAR(8) NOT NULL,
  rider_id BIGINT,
  candidate_count INT NOT NULL,
  candidate_details TEXT,
  decision_time_ms INT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_record_order ON t_dispatch_record (order_id);
CREATE INDEX idx_record_batch ON t_dispatch_record (batch_no);
CREATE INDEX idx_record_strategy ON t_dispatch_record (strategy);

CREATE TABLE IF NOT EXISTS t_batch_stat (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_no VARCHAR(32) NOT NULL,
  strategy VARCHAR(8) NOT NULL,
  order_count INT NOT NULL,
  rider_count INT NOT NULL,
  total_empty_distance DOUBLE,
  dispatch_time_ms INT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_dispatch_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cfg_key VARCHAR(64) NOT NULL UNIQUE,
  cfg_value VARCHAR(255) NOT NULL,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 派单默认配置（幂等种子，与 dispatch schema.sql 一致） ----------
INSERT INTO t_dispatch_config (cfg_key, cfg_value)
SELECT 'dispatch.mode', 'auto'
WHERE NOT EXISTS (SELECT 1 FROM t_dispatch_config WHERE cfg_key = 'dispatch.mode');

INSERT INTO t_dispatch_config (cfg_key, cfg_value)
SELECT 'dispatch.assignAfterCook', 'false'
WHERE NOT EXISTS (SELECT 1 FROM t_dispatch_config WHERE cfg_key = 'dispatch.assignAfterCook');

-- ---------- OP-3 Outbox 事务消息表 ----------
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
