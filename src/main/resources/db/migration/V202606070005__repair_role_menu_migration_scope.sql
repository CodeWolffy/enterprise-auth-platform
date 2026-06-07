-- =====================================================
-- 修复历史资源授权迁移遗漏：sys_role_resource 的 resource_id 可能指向 platform 资源模板
-- 已执行过 V202606070002 的数据库通过本迁移补齐 sys_role_menu
-- =====================================================

-- 补齐角色直接授权节点，角色租户保留，菜单 ID 指向 platform 菜单模板
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
  m.`id`,
  rr.`created_by`,
  rr.`created_at`
FROM `sys_role_resource` rr
INNER JOIN `sys_resource` r
  ON r.`id` = rr.`resource_id`
 AND r.`deleted` = 0
INNER JOIN `sys_menu` m
  ON m.`tenant_id` = 'platform'
 AND m.`resource_key` = r.`resource_key`
 AND m.`deleted` = 0
WHERE r.`resource_type` IN ('DIR', 'MENU', 'BUTTON', 'API')
  AND m.`menu_type` IN ('DIR', 'MENU', 'BUTTON', 'API')
ON DUPLICATE KEY UPDATE
  `created_by` = VALUES(`created_by`);

-- 补齐已授权节点祖先，保证登录菜单树可达
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
 AND r.`deleted` = 0
INNER JOIN `sys_menu` m
  ON m.`tenant_id` = 'platform'
 AND m.`resource_key` = r.`resource_key`
 AND m.`deleted` = 0
INNER JOIN `sys_menu` ancestor
  ON ancestor.`tenant_id` = 'platform'
 AND ancestor.`deleted` = 0
 AND FIND_IN_SET(CAST(ancestor.`id` AS CHAR) COLLATE utf8mb4_unicode_ci, m.`ancestors` COLLATE utf8mb4_unicode_ci) > 0
WHERE r.`resource_type` IN ('DIR', 'MENU', 'BUTTON', 'API')
  AND m.`menu_type` IN ('DIR', 'MENU', 'BUTTON', 'API')
  AND ancestor.`menu_type` IN ('DIR', 'MENU')
ON DUPLICATE KEY UPDATE
  `created_by` = VALUES(`created_by`);