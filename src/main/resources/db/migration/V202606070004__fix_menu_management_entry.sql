-- =====================================================
-- 修正历史菜单管理入口：resources -> menus
-- 保证旧库迁移后与前端路由白名单一致
-- =====================================================

UPDATE `sys_menu`
SET
  `resource_key` = 'menus',
  `menu_name` = '菜单管理',
  `route_key` = 'menus',
  `grant_key` = 'system:write',
  `path` = '/system/menus',
  `component` = 'MenuManagementView',
  `icon` = 'Tickets',
  `visible` = 1,
  `enabled` = 1,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `menu_type` = 'MENU'
  AND (
    `resource_key` = 'resources'
    OR `route_key` = 'resources'
    OR `path` = '/system/resources'
    OR `component` = 'ResourceManagementView'
  );

UPDATE `sys_resource`
SET
  `resource_key` = 'menus',
  `resource_name` = '菜单管理',
  `route_key` = 'menus',
  `grant_key` = 'system:write',
  `path` = '/system/menus',
  `component` = 'MenuManagementView',
  `icon` = 'Tickets',
  `visible` = 1,
  `enabled` = 1,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `resource_type` = 'MENU'
  AND (
    `resource_key` = 'resources'
    OR `route_key` = 'resources'
    OR `path` = '/system/resources'
    OR `component` = 'ResourceManagementView'
  );