CREATE TABLE `sys_codegen_allowlist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `table_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '允许生成的数据表名',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '说明',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_codegen_allowlist_tenant_table` (`tenant_id` ASC, `table_name` ASC) USING BTREE,
  INDEX `idx_codegen_allowlist_tenant_enabled` (`tenant_id` ASC, `enabled` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码生成表白名单' ROW_FORMAT = DYNAMIC;

INSERT INTO `sys_codegen_allowlist` (`tenant_id`, `table_name`, `description`, `enabled`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`)
VALUES
  ('platform', 'sys_config', '系统参数表，可用于代码生成示例', 1, 'system', 'system', 0, NOW(), NOW()),
  ('platform', 'sys_dict', '系统字典表，可用于代码生成示例', 1, 'system', 'system', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `description` = VALUES(`description`),
  `enabled` = VALUES(`enabled`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = VALUES(`deleted`),
  `updated_at` = NOW();

INSERT INTO `sys_resource` (
  `tenant_id`, `parent_id`, `ancestors`, `resource_type`, `resource_key`, `resource_name`, `route_key`, `grant_key`, `path`, `component`, `icon`, `order_no`, `visible`, `enabled`, `is_system`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`
)
SELECT 'platform', codegen.`id`, CONCAT(codegen.`ancestors`, ',', codegen.`id`), 'BUTTON', 'codegen-download', '代码生成下载', 'codegen-download', 'codegen:download', NULL, NULL, NULL, 10, 0, 1, 1, 'system', 'system', 0, NOW(), NOW()
FROM `sys_resource` codegen
WHERE codegen.`tenant_id` = 'platform' AND codegen.`resource_key` = 'codegen' AND codegen.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`), `ancestors` = VALUES(`ancestors`), `resource_type` = VALUES(`resource_type`), `resource_name` = VALUES(`resource_name`),
  `route_key` = VALUES(`route_key`), `grant_key` = VALUES(`grant_key`), `path` = VALUES(`path`), `component` = VALUES(`component`), `icon` = VALUES(`icon`),
  `order_no` = VALUES(`order_no`), `visible` = VALUES(`visible`), `enabled` = VALUES(`enabled`), `is_system` = VALUES(`is_system`), `updated_by` = VALUES(`updated_by`), `deleted` = VALUES(`deleted`), `updated_at` = NOW();

INSERT INTO `sys_resource` (
  `tenant_id`, `parent_id`, `ancestors`, `resource_type`, `resource_key`, `resource_name`, `route_key`, `grant_key`, `path`, `component`, `icon`, `order_no`, `visible`, `enabled`, `is_system`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`
)
SELECT 'platform', api.`id`, CONCAT(api.`ancestors`, ',', api.`id`), 'API', 'api.codegen.download', '代码生成下载', NULL, 'codegen:download', NULL, NULL, NULL, 250, 0, 1, 1, 'system', 'system', 0, NOW(), NOW()
FROM `sys_resource` api
WHERE api.`tenant_id` = 'platform' AND api.`resource_key` = 'api' AND api.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`), `ancestors` = VALUES(`ancestors`), `resource_type` = VALUES(`resource_type`), `resource_name` = VALUES(`resource_name`),
  `grant_key` = VALUES(`grant_key`), `order_no` = VALUES(`order_no`), `visible` = VALUES(`visible`), `enabled` = VALUES(`enabled`), `is_system` = VALUES(`is_system`),
  `updated_by` = VALUES(`updated_by`), `deleted` = VALUES(`deleted`), `updated_at` = NOW();

INSERT INTO `sys_role_resource` (`tenant_id`, `role_id`, `resource_id`, `created_by`, `updated_by`, `created_at`, `updated_at`)
SELECT 'platform', role.`id`, resource.`id`, 'system', 'system', NOW(), NOW()
FROM `sys_role` role
JOIN `sys_resource` resource
  ON resource.`tenant_id` = 'platform'
  AND resource.`resource_key` IN ('codegen-download', 'api.codegen.download')
  AND resource.`deleted` = 0
WHERE role.`tenant_id` = 'platform' AND role.`role_code` = 'ADMIN' AND role.`deleted` = 0
ON DUPLICATE KEY UPDATE `updated_by` = VALUES(`updated_by`), `updated_at` = NOW();