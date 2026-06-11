-- =====================================================
-- 菜单数据修正脚本：使菜单管理页面数据与期望截图一致
-- =====================================================

-- 1. 将 菜单管理 (id=28) 从 系统管理 移动到 平台管理
UPDATE `sys_menu` SET
  `parent_id` = 30,
  `ancestors` = '1,30',
  `path` = '/platform/menu',
  `component` = 'upms/menu/index',
  `grant_key` = NULL,
  `order_no` = 10,
  `is_system` = 0,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `id` = 28;

-- 2. 修正现有 平台管理 子菜单的属性
UPDATE `sys_menu` SET
  `path` = '/platform/dict',
  `component` = 'upms/dict/index',
  `order_no` = 20,
  `is_system` = 0,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `id` = 31;

UPDATE `sys_menu` SET
  `path` = '/platform/tenant',
  `component` = 'upms/tenant/index',
  `order_no` = 30,
  `is_system` = 0,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `id` = 24;

UPDATE `sys_menu` SET
  `path` = '/platform/tenant-package',
  `component` = 'upms/tenant-package/index',
  `order_no` = 40,
  `is_system` = 0,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `id` = 32;

UPDATE `sys_menu` SET
  `menu_name` = '系统参数',
  `path` = '/platform/config',
  `component` = 'upms/config/index',
  `order_no` = 50,
  `is_system` = 0,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `id` = 33;

-- 3. 将 平台管理 下多余的菜单移动到 系统管理 并隐藏
UPDATE `sys_menu` SET
  `parent_id` = 20,
  `ancestors` = '1,20',
  `visible` = 0,
  `is_system` = 0,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `id` IN (318, 34, 35, 321);

UPDATE `sys_menu` SET
  `parent_id` = 20,
  `ancestors` = '1,20',
  `visible` = 0,
  `is_system` = 0,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `id` = 329;

UPDATE `sys_menu` SET
  `parent_id` = 20,
  `ancestors` = '1,20',
  `visible` = 0,
  `is_system` = 0,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `id` = 344;

-- 4. 更新被移动菜单的 descendants 的 ancestors
UPDATE `sys_menu` SET
  `ancestors` = '1,20,329',
  `is_system` = 0,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `id` IN (330, 331, 332, 333, 345);

UPDATE `sys_menu` SET
  `ancestors` = '1,20,344',
  `is_system` = 0,
  `updated_by` = 'system',
  `updated_at` = NOW()
WHERE `id` = 346;

-- 5. 新增 平台管理 下的菜单
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `ancestors`, `menu_type`, `resource_key`, `menu_name`, `route_key`, `grant_key`, `path`, `component`, `icon`, `order_no`, `visible`, `enabled`, `is_system`, `created_by`, `updated_by`, `deleted`)
VALUES
  (1028, 'platform', 30, '1,30', 'MENU', 'server-monitor', '服务监控', NULL, NULL, '/platform/server', 'upms/sys-server/index', 'Monitor', 60, 1, 1, 0, 'system', 'system', 0),
  (1029, 'platform', 30, '1,30', 'MENU', 'copyright', '版权配置', NULL, NULL, '/copyright', 'upms/copyright/index', 'Document', 70, 1, 1, 0, 'system', 'system', 0),
  (1030, 'platform', 30, '1,30', 'MENU', 'logistics-company', '物流公司', NULL, NULL, '/shop/logistics-company', 'shop/logistics-company/index', 'OfficeBuilding', 80, 1, 1, 0, 'system', 'system', 0),
  (1031, 'platform', 30, '1,30', 'MENU', 'region', '行政区划', NULL, NULL, '/platform/region', 'upms/region/index', 'MapLocation', 90, 1, 1, 0, 'system', 'system', 0),
  (1032, 'platform', 30, '1,30', 'MENU', 'sms-package-config', '短信套餐', NULL, NULL, '/platform/sms-package-config', 'upms/sms-package-config/index', 'Message', 100, 1, 1, 0, 'system', 'system', 0),
  (1033, 'platform', 30, '1,30', 'MENU', 'open-component', '微信三方平台', NULL, NULL, '/open-component', 'upms/open-component/index', 'ChatDotRound', 110, 1, 1, 0, 'system', 'system', 0);

-- 6. 新增 菜单管理 下的按钮权限
INSERT INTO `sys_menu` (`id`, `tenant_id`, `parent_id`, `ancestors`, `menu_type`, `resource_key`, `menu_name`, `grant_key`, `order_no`, `visible`, `enabled`, `is_system`, `created_by`, `updated_by`, `deleted`)
VALUES
  (1034, 'platform', 28, '1,30,28', 'BUTTON', 'sysmenu.add', '菜单管理新增', 'upms:sysmenu:add', 10, 1, 1, 0, 'system', 'system', 0),
  (1035, 'platform', 28, '1,30,28', 'BUTTON', 'sysmenu.edit', '菜单管理修改', 'upms:sysmenu:edit', 20, 1, 1, 0, 'system', 'system', 0),
  (1036, 'platform', 28, '1,30,28', 'BUTTON', 'sysmenu.del', '菜单管理删除', 'upms:sysmenu:del', 30, 1, 1, 0, 'system', 'system', 0),
  (1037, 'platform', 28, '1,30,28', 'BUTTON', 'sysmenu.page', '菜单管理列表', 'upms:sysmenu:page', 40, 1, 1, 0, 'system', 'system', 0),
  (1038, 'platform', 28, '1,30,28', 'BUTTON', 'sysmenu.get', '菜单管理查询', 'upms:sysmenu:get', 50, 1, 1, 0, 'system', 'system', 0);

-- 7. 为 ADMIN 角色分配新菜单
INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`, `created_by`, `created_at`)
VALUES
  ('platform', 1, 1028, 'system', NOW()),
  ('platform', 1, 1029, 'system', NOW()),
  ('platform', 1, 1030, 'system', NOW()),
  ('platform', 1, 1031, 'system', NOW()),
  ('platform', 1, 1032, 'system', NOW()),
  ('platform', 1, 1033, 'system', NOW()),
  ('platform', 1, 1034, 'system', NOW()),
  ('platform', 1, 1035, 'system', NOW()),
  ('platform', 1, 1036, 'system', NOW()),
  ('platform', 1, 1037, 'system', NOW()),
  ('platform', 1, 1038, 'system', NOW())
ON DUPLICATE KEY UPDATE `created_by` = VALUES(`created_by`);

-- 8. 更新 sys_menu 自增值
ALTER TABLE `sys_menu` AUTO_INCREMENT = 1039;