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
);
CREATE INDEX IF NOT EXISTS idx_rider_status ON t_rider (status);

CREATE TABLE IF NOT EXISTS t_dispatch_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  batch_no VARCHAR(32) NOT NULL,
  strategy VARCHAR(8) NOT NULL,
  rider_id BIGINT,
  candidate_count INT NOT NULL,
  candidate_details CLOB,
  decision_time_ms INT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_record_order ON t_dispatch_record (order_id);
CREATE INDEX IF NOT EXISTS idx_record_batch ON t_dispatch_record (batch_no);
CREATE INDEX IF NOT EXISTS idx_record_strategy ON t_dispatch_record (strategy);

CREATE TABLE IF NOT EXISTS t_batch_stat (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_no VARCHAR(32) NOT NULL,
  strategy VARCHAR(8) NOT NULL,
  order_count INT NOT NULL,
  rider_count INT NOT NULL,
  total_empty_distance DOUBLE,
  dispatch_time_ms INT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_dispatch_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cfg_key VARCHAR(64) NOT NULL UNIQUE,
  cfg_value VARCHAR(255) NOT NULL,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ============ 种子数据 ============
INSERT INTO t_dispatch_config (cfg_key, cfg_value)
SELECT 'dispatch.mode', 'auto'
WHERE NOT EXISTS (SELECT 1 FROM t_dispatch_config WHERE cfg_key = 'dispatch.mode');

INSERT INTO t_dispatch_config (cfg_key, cfg_value)
SELECT 'dispatch.assignAfterCook', 'false'
WHERE NOT EXISTS (SELECT 1 FROM t_dispatch_config WHERE cfg_key = 'dispatch.assignAfterCook');
