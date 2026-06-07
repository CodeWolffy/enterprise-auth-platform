-- =====================================================
-- 历史资源数据迁移到菜单权限统一表
-- 来源：sys_resource + sys_role_resource
-- 目标：sys_menu + sys_role_menu
-- =====================================================

INSERT INTO `sys_menu` (
  `id`,
  `tenant_id`,
  `parent_id`,
  `ancestors`,
  `menu_type`,
  `resource_key`,
  `menu_name`,
  `route_key`,
  `grant_key`,
  `path`,
  `component`,
  `redirect`,
  `icon`,
  `order_no`,
  `visible`,
  `enabled`,
  `is_system`,
  `outer_status`,
  `application_key`,
  `created_by`,
  `updated_by`,
  `deleted`,
  `created_at`,
  `updated_at`
)
SELECT
  r.`id`,
  'platform',
  r.`parent_id`,
  COALESCE(r.`ancestors`, ''),
  r.`resource_type`,
  r.`resource_key`,
  r.`resource_name`,
  r.`route_key`,
  NULLIF(r.`grant_key`, ''),
  r.`path`,
  r.`component`,
  NULL,
  r.`icon`,
  r.`order_no`,
  r.`visible`,
  r.`enabled`,
  r.`is_system`,
  0,
  NULL,
  r.`created_by`,
  r.`updated_by`,
  r.`deleted`,
  r.`created_at`,
  r.`updated_at`
FROM `sys_resource` r
WHERE r.`tenant_id` = 'platform'
  AND r.`deleted` = 0
  AND r.`resource_type` IN ('DIR', 'MENU', 'BUTTON', 'API')
ON DUPLICATE KEY UPDATE
  `tenant_id` = VALUES(`tenant_id`),
  `parent_id` = VALUES(`parent_id`),
  `ancestors` = VALUES(`ancestors`),
  `menu_type` = VALUES(`menu_type`),
  `resource_key` = VALUES(`resource_key`),
  `menu_name` = VALUES(`menu_name`),
  `route_key` = VALUES(`route_key`),
  `grant_key` = VALUES(`grant_key`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `redirect` = VALUES(`redirect`),
  `icon` = VALUES(`icon`),
  `order_no` = VALUES(`order_no`),
  `visible` = VALUES(`visible`),
  `enabled` = VALUES(`enabled`),
  `is_system` = VALUES(`is_system`),
  `outer_status` = VALUES(`outer_status`),
  `application_key` = VALUES(`application_key`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = VALUES(`deleted`),
  `updated_at` = VALUES(`updated_at`);

-- 迁移角色直接授权节点，角色租户保留，菜单 ID 统一指向 platform 模板
INSERT INTO `sys_role_menu` (
  `tenant_id`,
  `role_id`,
  `menu_id`,
  `created_by`,
  `created_at`
)
SELECT
  rr.`tenant_id`,
  rr.`role_id`,
  template.`id`,
  rr.`created_by`,
  rr.`created_at`
FROM `sys_role_resource` rr
INNER JOIN `sys_resource` r
  ON r.`id` = rr.`resource_id`
 AND r.`tenant_id` = rr.`tenant_id`
 AND r.`deleted` = 0
INNER JOIN `sys_resource` template
  ON template.`tenant_id` = 'platform'
 AND template.`resource_key` = r.`resource_key`
 AND template.`deleted` = 0
WHERE r.`resource_type` IN ('DIR', 'MENU', 'BUTTON', 'API')
  AND template.`resource_type` IN ('DIR', 'MENU', 'BUTTON', 'API')
ON DUPLICATE KEY UPDATE
  `created_by` = VALUES(`created_by`);

-- 补齐已授权节点的祖先，保证登录菜单树可达
INSERT INTO `sys_role_menu` (
  `tenant_id`,
  `role_id`,
  `menu_id`,
  `created_by`,
  `created_at`
)
SELECT DISTINCT
  rr.`tenant_id`,
  rr.`role_id`,
  ancestor.`id`,
  rr.`created_by`,
  rr.`created_at`
FROM `sys_role_resource` rr
INNER JOIN `sys_resource` r
  ON r.`id` = rr.`resource_id`
 AND r.`tenant_id` = rr.`tenant_id`
 AND r.`deleted` = 0
INNER JOIN `sys_resource` template
  ON template.`tenant_id` = 'platform'
 AND template.`resource_key` = r.`resource_key`
 AND template.`deleted` = 0
INNER JOIN `sys_resource` ancestor
  ON ancestor.`tenant_id` = 'platform'
 AND ancestor.`deleted` = 0
 AND FIND_IN_SET(CAST(ancestor.`id` AS CHAR) COLLATE utf8mb4_unicode_ci, template.`ancestors` COLLATE utf8mb4_unicode_ci) > 0
WHERE r.`resource_type` IN ('DIR', 'MENU', 'BUTTON', 'API')
  AND template.`resource_type` IN ('DIR', 'MENU', 'BUTTON', 'API')
  AND ancestor.`resource_type` IN ('DIR', 'MENU')
ON DUPLICATE KEY UPDATE
  `created_by` = VALUES(`created_by`);