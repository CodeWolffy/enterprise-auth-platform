-- ----------------------------------------------------------------------------
-- V18: 代码生成与数据源管理按独立页面入口注册
--
-- 背景：frontend-vben 使用后端菜单生成动态路由。若一个有 component 的页面
--      同时承载子页面，会形成“页面套页面”的路由结构，导致 /platform/codegen
--      被子页面重定向或覆盖。
-- 处理：代码生成、数据源管理都归属平台管理，路由层保持独立页面。
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';
SET NAMES utf8mb4;

UPDATE `sys_menu`
SET
    `parent_id` = 300,
    `name` = '数据源管理',
    `permission` = NULL,
    `path` = '/platform/codegen/datasource',
    `component` = 'gen/datasource/index',
    `sort` = 95,
    `type` = '0',
    `redirect` = NULL,
    `icon` = 'carbon:database',
    `outer_status` = 0,
    `application_key` = 'dev',
    `deleted` = 0
WHERE `id` = 397;