CREATE DATABASE IF NOT EXISTS mall_dispatch DEFAULT CHARACTER SET utf8mb4;
USE mall_dispatch;

CREATE TABLE t_rider (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  name VARCHAR(32) NOT NULL COMMENT '骑手姓名',
  phone VARCHAR(16) NOT NULL COMMENT '手机号',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0休息/1在线/2配送中',
  current_lng DOUBLE COMMENT '当前经度',
  current_lat DOUBLE COMMENT '当前纬度',
  capacity INT NOT NULL DEFAULT 3 COMMENT '同时持单上限',
  accept_count INT NOT NULL DEFAULT 0 COMMENT '历史接单数',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='骑手表';

CREATE TABLE t_dispatch_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  batch_no VARCHAR(32) NOT NULL COMMENT '批次号',
  strategy VARCHAR(8) NOT NULL COMMENT '策略：A/B/C',
  rider_id BIGINT COMMENT '指派骑手ID',
  candidate_count INT NOT NULL COMMENT '候选骑手数',
  candidate_details JSON COMMENT '候选得分明细（JSON）',
  decision_time_ms INT NOT NULL COMMENT '决策耗时（毫秒）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_order_id (order_id),
  INDEX idx_batch_no (batch_no),
  INDEX idx_strategy (strategy),
  INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单记录表';

CREATE TABLE t_batch_stat (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  batch_no VARCHAR(32) NOT NULL COMMENT '批次号',
  strategy VARCHAR(8) NOT NULL COMMENT '策略：A/B/C',
  order_count INT NOT NULL COMMENT '订单数',
  rider_count INT NOT NULL COMMENT '骑手数',
  total_empty_distance DOUBLE COMMENT '总空驶距离（米）',
  dispatch_time_ms INT NOT NULL COMMENT '批次派单耗时（毫秒）',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_batch_no (batch_no),
  INDEX idx_strategy (strategy)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次统计表';

CREATE TABLE t_dispatch_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  cfg_key VARCHAR(64) NOT NULL UNIQUE COMMENT '配置键',
  cfg_value VARCHAR(255) NOT NULL COMMENT '配置值',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派单配置表';

-- ============ 种子数据 ============
INSERT INTO t_rider (id,name,phone,status,current_lng,current_lat,capacity) VALUES
(4,'骑手张三','13800000011',0,113.660,34.762,3),
(5,'骑手李四','13800000012',0,113.657,34.765,3),
(6,'骑手王五','13800000013',0,113.663,34.760,3);

INSERT INTO t_dispatch_config (cfg_key,cfg_value) VALUES
('dispatch.strategy','B'),
('dispatch.mode','auto'),
('dispatch.assignAfterCook','false');
