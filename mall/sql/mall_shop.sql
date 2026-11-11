CREATE DATABASE IF NOT EXISTS mall_shop DEFAULT CHARACTER SET utf8mb4;
USE mall_shop;

CREATE TABLE t_shop (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  name VARCHAR(64) NOT NULL COMMENT '商家名称',
  address VARCHAR(255) NOT NULL COMMENT '商家地址',
  lng DOUBLE NOT NULL COMMENT '商家经度（取货点）',
  lat DOUBLE NOT NULL COMMENT '商家纬度',
  phone VARCHAR(16) COMMENT '联系电话',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '营业状态：0打烊/1营业',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家表';

CREATE TABLE t_category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  shop_id BIGINT NOT NULL COMMENT '商家ID',
  name VARCHAR(32) NOT NULL COMMENT '分类名称',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  INDEX idx_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品分类表';

CREATE TABLE t_dish (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  shop_id BIGINT NOT NULL COMMENT '商家ID',
  category_id BIGINT NOT NULL COMMENT '分类ID',
  name VARCHAR(64) NOT NULL COMMENT '菜品名称',
  price DECIMAL(10,2) NOT NULL COMMENT '单价',
  image VARCHAR(255) COMMENT '图片URL',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0停售/1起售',
  sold_out TINYINT NOT NULL DEFAULT 0 COMMENT '沽清：0正常/1沽清',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除/1已删除',
  INDEX idx_shop_id (shop_id),
  INDEX idx_category_id (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';

-- ============ 种子数据 ============
INSERT INTO t_shop (name,address,lng,lat,phone,status) VALUES
('张姐麻辣烫','科学大道 100 号',113.658,34.763,'13800000001',1),
('老王盖浇饭','长椿路 45 号',113.661,34.758,'13800000002',1),
('校园汉堡店','莲花街 8 号',113.655,34.766,'13800000003',1);

INSERT INTO t_category (shop_id,name,sort) VALUES
(1,'热菜',1),
(1,'主食',2),
(2,'盖浇饭',1),
(2,'汤类',2),
(3,'汉堡',1),
(3,'饮品',2);

INSERT INTO t_dish (shop_id,category_id,name,price,status) VALUES
(1,1,'麻辣烫(自选)',18.00,1),
(1,1,'麻辣拌',16.00,1),
(1,2,'米饭',2.00,1),
(2,3,'土豆牛肉盖饭',15.00,1),
(2,3,'番茄鸡蛋盖饭',12.00,1),
(2,4,'紫菜蛋花汤',4.00,1),
(3,5,'香辣鸡腿堡',14.00,1),
(3,5,'双层牛肉堡',19.00,1),
(3,6,'冰可乐',5.00,1);
