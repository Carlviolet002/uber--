-- 007: 管理端评价审核字段（mall-order t_review）
-- 在管理端 P1 评价管理中使用：平台审核（下架违规）、平台回复。
-- MySQL / H2(MySQL 兼容模式) 通用。H2 若已建旧表需执行本脚本或清理重建。

ALTER TABLE t_review
    ADD COLUMN audit_status TINYINT NOT NULL DEFAULT 1 COMMENT '审核状态 1通过 2下架',
    ADD COLUMN audit_time DATETIME DEFAULT NULL COMMENT '审核时间',
    ADD COLUMN platform_reply VARCHAR(512) DEFAULT NULL COMMENT '平台回复',
    ADD COLUMN reply_by VARCHAR(16) DEFAULT NULL COMMENT '回复来源 SHOP/PLATFORM';
