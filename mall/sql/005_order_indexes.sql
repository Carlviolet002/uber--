-- OP-10 数据层优化：t_order 复合索引补齐
-- 背景：t_order 单列索引（order_no 唯一、user_id、shop_id、rider_id、status、created_at）均已存在；
--       这里补一个 (status, create_time) 复合索引，服务"按状态 + 时间范围"的高频查询
--       （如超时关单扫描器 PayTimeoutScanner：status=? AND create_time < ?），避免回表全扫。
-- 执行位置：mall_db（t_order 所在库）
--   mysql -h<host> -u<user> -p<pass> mall_db < sql/005_order_indexes.sql
-- 注：单库单表 + 读写分离/分片列为"未来工作"，不在本次实现范围（见 docs/架构优化改造方案.md OP-10）。

CREATE INDEX idx_order_status_ctime ON t_order (status, create_time);
