-- OP-8 幂等兜底：为已存在的 t_order 增加 uniq_token 唯一约束
-- 作用：Redis 过期后用户用同一 idempotentKey 重试下单时，唯一约束拦截重复插入，
--       订单服务捕获 DuplicateKeyException 后返回已存在的订单，避免生成重复订单。
--
-- 执行位置：在 mall_order 库（t_order 所在库）执行，例如：
--   mysql -h<host> -u<user> -p<pass> mall_order < sql/004_order_uniq_token.sql
--
-- 说明：MySQL 的 UNIQUE 允许多个 NULL 值，历史订单 uniq_token 为 NULL 不会冲突；
--       新订单都会写入 uniq_token，重复 key 将被唯一约束拦截。

ALTER TABLE t_order
  ADD COLUMN uniq_token VARCHAR(64) NULL
  COMMENT '幂等令牌（客户端 idempotentKey），唯一约束兜底防重复下单';

ALTER TABLE t_order
  ADD UNIQUE KEY uk_uniq_token (uniq_token);
