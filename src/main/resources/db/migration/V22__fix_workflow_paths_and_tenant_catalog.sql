-- ----------------------------------------------------------------------------
-- V22: 修复工作流路由路径 & 租户套餐组件路径对齐 vben 前端
--
-- 背景：
--   1. V17 将工作流子菜单 path 设为 /platform/workflow/*，但 frontend-vben
--      实际注册的路由为 /workflow/*，导致侧边栏点击后路由不匹配。
--   2. V21 修复租户套餐 component 的迁移未生效（或未覆盖），
--      浏览器控制台仍报 "route component is invalid: /system/TenantCatalogView.vue"
--   3. 工作流"我的发起"子菜单 path 使用 /my-instances，vben 路由为 /instances
--   4. 工作流子菜单 component 已由 V17 修正，本迁移仅修正 path 和对齐
--
-- 修复策略：
--   * 工作流父级(400) 保持 path=/workflow（V3 已设定）
--   * 所有工作流子菜单 path 改为 /workflow/* 与 vben 路由完全对齐
--   * 租户套餐(320) component 修正为 system/tenant-catalog/index
--   * 其他 component 如有残留旧值一并修正
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';
SET NAMES utf8mb4;

-- ============================
-- 1. 工作流父级菜单：确保 path 为 /workflow
-- ============================
UPDATE `sys_menu`
SET
    `path`      = '/workflow',
    `component` = NULL,
    `redirect`  = '/workflow/definitions'
WHERE `id` = 400;

-- ============================
-- 2. 工作流子菜单：path 从 /platform/workflow/* 改为 /workflow/*
--    component 已由 V17 修正，此处作为幂等保障
-- ============================

-- 410: 流程设计器
UPDATE `sys_menu`
SET
    `path`      = '/workflow/designer',
    `component` = 'workflow/designer'
WHERE `id` = 410;

-- 420: 流程定义
UPDATE `sys_menu`
SET
    `path`      = '/workflow/definitions',
    `component` = 'workflow/definitions'
WHERE `id` = 420;

-- 430: 我的发起（path 从 my-instances 改为 instances，与 vben 路由对齐）
UPDATE `sys_menu`
SET
    `path`      = '/workflow/instances',
    `component` = 'workflow/instances'
WHERE `id` = 430;

-- 440: 我的待办
UPDATE `sys_menu`
SET
    `path`      = '/workflow/todo',
    `component` = 'workflow/todo'
WHERE `id` = 440;

-- 450: 我的已办
UPDATE `sys_menu`
SET
    `path`      = '/workflow/done',
    `component` = 'workflow/done'
WHERE `id` = 450;

-- ============================
-- 3. 租户套餐(320)：修正 component 为 vben 实际路径
-- ============================
UPDATE `sys_menu`
SET
    `component` = 'system/tenant-catalog/index'
WHERE `id` = 320
   OR `component` = 'system/TenantCatalogView';

-- ============================
-- 4. 系统设置(260)：确保 component 为 SystemManagementView（V12 已修正，幂等）
-- ============================
UPDATE `sys_menu`
SET `component` = 'system/SystemManagementView'
WHERE `id` = 260 AND `component` != 'system/SystemManagementView';

-- ============================
-- 5. 运行总览(100)：确保 component 正确（V7 已修正，幂等）
-- ============================
UPDATE `sys_menu`
SET `component` = 'dashboard/platform/index'
WHERE `id` = 100 AND `component` != 'dashboard/platform/index';

-- ============================
-- 6. 平台管理(300)：确保为纯目录
-- ============================
UPDATE `sys_menu`
SET
    `path`      = '/platform',
    `component` = NULL,
    `redirect`  = NULL
WHERE `id` = 300;

-- ============================
-- 7. 系统管理(200)：确保为纯目录，path 与 vben 路由对齐
-- ============================
UPDATE `sys_menu`
SET
    `path`      = '/system',
    `component` = NULL,
    `redirect`  = '/system/overview'
WHERE `id` = 200;