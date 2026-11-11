CREATE DATABASE IF NOT EXISTS mall_user DEFAULT CHARACTER SET utf8mb4;
USE mall_user;

CREATE TABLE t_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  username VARCHAR(32) NOT NULL UNIQUE COMMENT '用户名',
  password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
  nickname VARCHAR(32) COMMENT '昵称',
  phone VARCHAR(16) COMMENT '手机号',
  role VARCHAR(8) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/SHOP/RIDER/ADMIN（legacy 单值，取位掩码主角色）',
  roles BIGINT NOT NULL DEFAULT 1 COMMENT '角色位掩码：USER=1/SHOP=2/RIDER=4/ADMIN=8',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE t_address (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  receiver_name VARCHAR(32) NOT NULL COMMENT '收货人姓名',
  receiver_phone VARCHAR(16) NOT NULL COMMENT '收货人手机号',
  lng DOUBLE NOT NULL COMMENT '经度',
  lat DOUBLE NOT NULL COMMENT '纬度',
  detail VARCHAR(255) NOT NULL COMMENT '详细地址',
  is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认：0否/1是',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';

-- ============ 种子数据 ============
-- 以下密码需替换为 BCrypt('123456') 的哈希，可在任意 Spring 应用里用 new BCryptPasswordEncoder().encode("123456") 生成
-- roles 位掩码：USER=1 / SHOP=2 / RIDER=4 / ADMIN=8
INSERT INTO t_user (username,password,nickname,role,roles) VALUES
('user1','<BCRYPT_HASH>','用户1','USER',1),
('shop1','<BCRYPT_HASH>','商家1','SHOP',2),
('rider1','<BCRYPT_HASH>','骑手1','RIDER',4),
('admin','<BCRYPT_HASH>','管理员','ADMIN',8);

-- 注意：手动执行本文件初始化时 user1 为 AUTO_INCREMENT id=1；
-- 若由应用启动时 SeedService 自动播种（雪花 id），请勿同时执行此段，以免 address.user_id 不匹配。
INSERT INTO t_address (user_id,receiver_name,receiver_phone,lng,lat,detail,is_default) VALUES
(1,'用户1','13800138000',113.66,34.76,'郑州大学主校区 3 号楼',1);
