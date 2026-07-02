-- ----------------------------------------------------------------------------
-- V17: 工作流菜单指向已迁移的 Vben 独立页面
--
-- 背景：V5/V6 为了避免 404，将五个工作流入口临时统一指向 workflow/index。
-- 当前 frontend-vben 已具备 designer/definitions/instances/todo/done 独立页面，
-- 后端菜单应直接指向对应视图，否则运行态只会进入工作流入口页。
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';
SET NAMES utf8mb4;

UPDATE `sys_menu`
SET
    `path` = '/platform/workflow/designer',
    `component` = 'workflow/designer',
    `redirect` = NULL,
    `deleted` = 0
WHERE `id` = 410;

UPDATE `sys_menu`
SET
    `path` = '/platform/workflow/definitions',
    `component` = 'workflow/definitions',
    `redirect` = NULL,
    `deleted` = 0
WHERE `id` = 420;

UPDATE `sys_menu`
SET
    `path` = '/platform/workflow/my-instances',
    `component` = 'workflow/instances',
    `redirect` = NULL,
    `deleted` = 0
WHERE `id` = 430;

UPDATE `sys_menu`
SET
    `path` = '/platform/workflow/todo',
    `component` = 'workflow/todo',
    `redirect` = NULL,
    `deleted` = 0
WHERE `id` = 440;

UPDATE `sys_menu`
SET
    `path` = '/platform/workflow/done',
    `component` = 'workflow/done',
    `redirect` = NULL,
    `deleted` = 0
WHERE `id` = 450;