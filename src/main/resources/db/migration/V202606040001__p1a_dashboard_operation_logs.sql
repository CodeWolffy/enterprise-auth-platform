INSERT INTO `sys_resource` (
  `tenant_id`,
  `parent_id`,
  `ancestors`,
  `resource_type`,
  `resource_key`,
  `resource_name`,
  `route_key`,
  `grant_key`,
  `path`,
  `component`,
  `icon`,
  `order_no`,
  `visible`,
  `enabled`,
  `is_system`,
  `created_by`,
  `updated_by`,
  `deleted`,
  `created_at`,
  `updated_at`
)
SELECT
  'platform',
  parent.`id`,
  CONCAT('1,', parent.`id`),
  'MENU',
  'operation-logs',
  '操作日志',
  'operation-logs',
  'operation-log:read',
  '/system/operation-logs',
  'OperationLogView',
  'Document',
  45,
  1,
  1,
  1,
  'system',
  'system',
  0,
  NOW(),
  NOW()
FROM `sys_resource` parent
WHERE parent.`tenant_id` = 'platform'
  AND parent.`resource_key` = 'system'
  AND parent.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `ancestors` = VALUES(`ancestors`),
  `resource_type` = VALUES(`resource_type`),
  `resource_name` = VALUES(`resource_name`),
  `route_key` = VALUES(`route_key`),
  `grant_key` = VALUES(`grant_key`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `order_no` = VALUES(`order_no`),
  `visible` = VALUES(`visible`),
  `enabled` = VALUES(`enabled`),
  `is_system` = VALUES(`is_system`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = VALUES(`deleted`),
  `updated_at` = NOW();

UPDATE `sys_resource`
SET `grant_key` = 'dashboard:read',
    `updated_by` = 'system',
    `updated_at` = NOW()
WHERE `tenant_id` = 'platform'
  AND `resource_key` = 'dashboard'
  AND `deleted` = 0;

INSERT INTO `sys_resource` (
  `tenant_id`,
  `parent_id`,
  `ancestors`,
  `resource_type`,
  `resource_key`,
  `resource_name`,
  `route_key`,
  `grant_key`,
  `path`,
  `component`,
  `icon`,
  `order_no`,
  `visible`,
  `enabled`,
  `is_system`,
  `created_by`,
  `updated_by`,
  `deleted`,
  `created_at`,
  `updated_at`
)
SELECT
  'platform',
  parent.`id`,
  CONCAT('1,', parent.`id`),
  'API',
  seed.`resource_key`,
  seed.`resource_name`,
  NULL,
  seed.`grant_key`,
  NULL,
  NULL,
  NULL,
  seed.`order_no`,
  0,
  1,
  1,
  'system',
  'system',
  0,
  NOW(),
  NOW()
FROM `sys_resource` parent
JOIN (
  SELECT 'api.dashboard.read' AS resource_key, '仪表盘读' AS resource_name, 'dashboard:read' AS grant_key, 180 AS order_no
  UNION ALL SELECT 'api.operation-log.read', '操作日志读', 'operation-log:read', 190
  UNION ALL SELECT 'api.operation-log.export', '操作日志导出', 'operation-log:export', 200
) seed
WHERE parent.`tenant_id` = 'platform'
  AND parent.`resource_key` = 'api'
  AND parent.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `ancestors` = VALUES(`ancestors`),
  `resource_type` = VALUES(`resource_type`),
  `resource_name` = VALUES(`resource_name`),
  `grant_key` = VALUES(`grant_key`),
  `order_no` = VALUES(`order_no`),
  `visible` = VALUES(`visible`),
  `enabled` = VALUES(`enabled`),
  `is_system` = VALUES(`is_system`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = VALUES(`deleted`),
  `updated_at` = NOW();

INSERT INTO `sys_role_resource` (
  `tenant_id`,
  `role_id`,
  `resource_id`,
  `created_by`,
  `updated_by`,
  `created_at`,
  `updated_at`
)
SELECT
  'platform',
  role.`id`,
  resource.`id`,
  'system',
  'system',
  NOW(),
  NOW()
FROM `sys_role` role
JOIN `sys_resource` resource
  ON resource.`tenant_id` = 'platform'
  AND resource.`resource_key` IN ('dashboard', 'operation-logs', 'api.dashboard.read', 'api.operation-log.read', 'api.operation-log.export')
  AND resource.`deleted` = 0
WHERE role.`tenant_id` = 'platform'
  AND role.`role_code` = 'ADMIN'
  AND role.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = NOW();