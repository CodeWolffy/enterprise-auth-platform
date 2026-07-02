-- ----------------------------------------------------------------------------
-- V12: 将系统设置主入口对齐到 Vben 的系统总览页
--
-- 说明：
--   * 前端新系统首页为 /system/overview -> SystemManagementView
--   * 旧的 /system/settings 仍可作为历史兼容路径由静态路由承接
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

UPDATE `sys_menu`
SET
    `name` = '系统设置',
    `path` = '/system/overview',
    `component` = 'system/SystemManagementView',
    `redirect` = NULL,
    `icon` = 'carbon:settings',
    `application_key` = 'system'
WHERE `id` = 260;
