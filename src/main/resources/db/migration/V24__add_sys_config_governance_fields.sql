-- ----------------------------------------------------------------------------
-- V24: Add runtime governance fields for system config management.
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

ALTER TABLE `sys_config`
  ADD COLUMN `config_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'business' COMMENT '参数类型：business 业务参数，system 系统参数' AFTER `config_value`,
  ADD COLUMN `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态：0 停用，1 启用' AFTER `config_type`,
  ADD COLUMN `builtin` tinyint NOT NULL DEFAULT 0 COMMENT '是否内置：0 否，1 是' AFTER `enabled`;

CREATE INDEX `idx_sys_config_tenant_type_enabled`
  ON `sys_config` (`tenant_id`, `config_type`, `enabled`, `deleted`);