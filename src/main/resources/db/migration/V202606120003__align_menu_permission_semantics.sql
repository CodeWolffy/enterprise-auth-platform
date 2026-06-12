-- =====================================================
-- 收敛菜单权限语义到 haorong-mall 模型
-- sys_menu.type = 0: 菜单节点，只承载路径、编码、图标、排序
-- sys_menu.type = 1: 按钮权限节点，只承载 permission/grant_key
-- =====================================================

UPDATE `sys_menu`
SET `grant_key` = NULL
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `menu_type` = '0';

UPDATE `sys_menu`
SET `path` = NULL,
    `component` = NULL,
    `redirect` = NULL,
    `route_key` = NULL,
    `icon` = NULL,
    `outer_status` = 0
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `menu_type` = '1';