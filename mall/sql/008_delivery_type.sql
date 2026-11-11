-- 008: 订单配送方式（外卖配送 / 到店自提）
-- DELIVERY = 外卖配送（默认，进入派单池由骑手派送，收配送费）
-- PICKUP   = 到店自提（不进入派单池、免配送费、可免填收货地址）
-- 存量订单默认回填 DELIVERY，兼容既有数据。
-- 执行位置：mall_db（订单库）
--   mysql -h<host> -u<user> -p<pass> mall_db < sql/008_delivery_type.sql

ALTER TABLE t_order
    ADD COLUMN delivery_type VARCHAR(16) NOT NULL DEFAULT 'DELIVERY'
    COMMENT '配送方式 DELIVERY=外卖配送 PICKUP=到店自提' AFTER delivery_fee;
