INSERT INTO `sys_resource` (
  `tenant_id`, `parent_id`, `ancestors`, `resource_type`, `resource_key`, `resource_name`, `route_key`, `grant_key`, `path`, `component`, `icon`, `order_no`, `visible`, `enabled`, `is_system`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`
)
SELECT 'platform', parent.`id`, CONCAT('1,', parent.`id`), 'MENU', 'codegen', '代码生成', 'codegen', 'codegen:read', '/platform/codegen', 'CodegenView', 'Document', 80, 1, 1, 1, 'system', 'system', 0, NOW(), NOW()
FROM `sys_resource` parent
WHERE parent.`tenant_id` = 'platform' AND parent.`resource_key` = 'platform-management' AND parent.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`), `ancestors` = VALUES(`ancestors`), `resource_type` = VALUES(`resource_type`), `resource_name` = VALUES(`resource_name`),
  `route_key` = VALUES(`route_key`), `grant_key` = VALUES(`grant_key`), `path` = VALUES(`path`), `component` = VALUES(`component`), `icon` = VALUES(`icon`),
  `order_no` = VALUES(`order_no`), `visible` = VALUES(`visible`), `enabled` = VALUES(`enabled`), `is_system` = VALUES(`is_system`), `updated_by` = VALUES(`updated_by`), `deleted` = VALUES(`deleted`), `updated_at` = NOW();

INSERT INTO `sys_role_resource` (`tenant_id`, `role_id`, `resource_id`, `created_by`, `updated_by`, `created_at`, `updated_at`)
SELECT 'platform', role.`id`, resource.`id`, 'system', 'system', NOW(), NOW()
FROM `sys_role` role
JOIN `sys_resource` resource
  ON resource.`tenant_id` = 'platform'
  AND resource.`resource_key` IN ('codegen')
  AND resource.`deleted` = 0
WHERE role.`tenant_id` = 'platform' AND role.`role_code` = 'ADMIN' AND role.`deleted` = 0
ON DUPLICATE KEY UPDATE `updated_by` = VALUES(`updated_by`), `updated_at` = NOW();