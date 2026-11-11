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
);
CREATE INDEX IF NOT EXISTS idx_order_user ON t_order (user_id);
CREATE INDEX IF NOT EXISTS idx_order_shop ON t_order (shop_id);
CREATE INDEX IF NOT EXISTS idx_order_rider ON t_order (rider_id);
CREATE INDEX IF NOT EXISTS idx_order_status ON t_order (status);
CREATE INDEX IF NOT EXISTS idx_order_created ON t_order (created_at);

CREATE TABLE IF NOT EXISTS t_order_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  dish_id BIGINT NOT NULL,
  dish_name VARCHAR(64) NOT NULL,
  num INT NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_detail_order ON t_order_detail (order_id);

CREATE TABLE IF NOT EXISTS t_order_status_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  from_status TINYINT,
  to_status TINYINT NOT NULL,
  operator_type VARCHAR(16) NOT NULL,
  operator_id BIGINT,
  time DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_log_order ON t_order_status_log (order_id);

CREATE TABLE IF NOT EXISTS t_payment_mock (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL,
  trade_no VARCHAR(64) NOT NULL,
  pay_amount DECIMAL(10,2) NOT NULL,
  callback_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

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
  audit_status TINYINT DEFAULT 1,
  audit_time DATETIME,
  platform_reply VARCHAR(512),
  reply_by VARCHAR(16),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_review_shop ON t_review (shop_id);
CREATE INDEX IF NOT EXISTS idx_review_order ON t_review (order_id);

-- OP-3 Outbox 事务消息：与订单同事务写入，独立投递器扫描发送（至少一次）
CREATE TABLE IF NOT EXISTS t_outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_type VARCHAR(64) NOT NULL,
  payload TEXT NOT NULL,
  delay_minutes BIGINT DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 0,
  retry_count INT NOT NULL DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  sent_time DATETIME
);
CREATE INDEX IF NOT EXISTS idx_outbox_status ON t_outbox (status, id);
