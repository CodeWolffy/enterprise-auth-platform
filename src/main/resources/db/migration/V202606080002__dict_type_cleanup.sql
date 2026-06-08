-- =====================================================
-- P2 字典二级模型语义收敛：字典类型补充运营字段
-- =====================================================

ALTER TABLE `sys_dict`
  ADD COLUMN `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '字典类型说明' AFTER `dict_value`,
  ADD COLUMN `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态：0 停用，1 启用' AFTER `description`,
  ADD COLUMN `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注' AFTER `enabled`;

UPDATE `sys_dict`
SET `description` = `dict_value`
WHERE `deleted` = 0
  AND (`description` IS NULL OR `description` = '')
  AND `dict_value` IS NOT NULL
  AND `dict_value` <> '';

CREATE INDEX `idx_sys_dict_tenant_enabled_updated`
  ON `sys_dict` (`tenant_id`, `deleted`, `enabled`, `updated_at`);