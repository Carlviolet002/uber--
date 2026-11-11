-- 003 新增角色位掩码列 roles（多角色支持，双兼容过渡）
-- 适用于已存在 mall_db 的情况；若 t_user 已有 roles 列请跳过。

-- 1) 新增列（已存在则忽略报错）。
--    ⚠️ 默认必须 0（不是 1）：否则已有行会被默认填成 1，导致下面的回填 UPDATE 因
--    "WHERE roles=0 OR roles IS NULL" 永远匹配不上，shop/rider/admin 的位掩码永远停在 1。
ALTER TABLE t_user ADD COLUMN roles BIGINT NOT NULL DEFAULT 0 COMMENT '角色位掩码：USER=1/SHOP=2/RIDER=4/ADMIN=8';

-- 2) 按 legacy 单值 role 回填位掩码（无条件回填：以 legacy role 为准，幂等可重复执行）。
--    若改成带 "roles=0/NULL" 的守卫，在上一步 DEFAULT 1 的情况下会失效，切勿再加回守卫。
UPDATE t_user SET roles = 1 WHERE role = 'USER';
UPDATE t_user SET roles = 2 WHERE role = 'SHOP';
UPDATE t_user SET roles = 4 WHERE role = 'RIDER';
UPDATE t_user SET roles = 8 WHERE role = 'ADMIN';
