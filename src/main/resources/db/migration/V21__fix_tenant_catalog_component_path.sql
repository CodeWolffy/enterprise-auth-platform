-- ----------------------------------------------------------------------------
-- V21: 修正租户套餐菜单的 component 路径
--
-- 说明：
--   * V15 中将 component 误写为 system/TenantCatalogView
--   * frontend-vben 中实际页面文件为 system/tenant-catalog/index.vue
--   * 本迁移覆盖已执行过 V15 的环境，将 component 修正为正确路径
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';
SET NAMES utf8mb4;

UPDATE `sys_menu`
SET
    `component` = 'system/tenant-catalog/index'
WHERE `component` = 'system/TenantCatalogView'
   OR `path` = '/platform/tenant-catalog';