-- OP-3 Outbox 事务消息表（real/MySQL）
-- 作用：订单创建/超时等事件与业务写入同一事务落库，独立投递器扫描发送到 MQ 并标记，
--       实现"至少一次"投递，消除"事务提交后发 MQ 但进程崩溃导致事件丢失"的窗口。
-- 执行位置：mall_db（订单库）
--   mysql -h<host> -u<user> -p<pass> mall_db < sql/006_outbox.sql
-- 状态：0=待投递 1=已投递 2=死信(重试超限)

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
