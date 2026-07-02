-- ----------------------------------------------------------------------------
-- V23: Upgrade system config management metadata.
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

ALTER TABLE `sys_config`
  ADD COLUMN `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注' AFTER `config_value`;

CREATE INDEX `idx_sys_config_tenant_deleted_updated_at`
  ON `sys_config` (`tenant_id`, `deleted`, `updated_at`);
