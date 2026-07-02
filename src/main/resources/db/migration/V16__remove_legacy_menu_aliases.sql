-- ----------------------------------------------------------------------------
-- V16: 收口平台/系统/代码生成菜单入口，清理旧别名
--
-- 说明：
--   * 仅处理现有 sys_menu 数据，不修改已执行过的历史迁移
--   * 保留唯一主入口：/system/overview、/platform、/platform/codegen
--   * 清理历史上可能残留的旧别名路径
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';
SET NAMES utf8mb4;

-- 1) 统一根菜单与正式入口
UPDATE `sys_menu`
SET
    `name` = '系统设置',
    `path` = '/system/overview',
    `component` = 'system/SystemManagementView',
    `redirect` = NULL,
    `icon` = 'carbon:settings',
    `application_key` = 'system',
    `deleted` = 0
WHERE `id` = 260;

UPDATE `sys_menu`
SET
    `name` = '平台管理',
    `path` = '/platform',
    `component` = NULL,
    `redirect` = NULL,
    `icon` = 'Platform',
    `application_key` = 'platform',
    `deleted` = 0
WHERE `id` = 300;

UPDATE `sys_menu`
SET
    `parent_id` = 300,
    `name` = '代码生成',
    `permission` = NULL,
    `path` = '/platform/codegen',
    `component` = 'gen/gen-table/index',
    `sort` = 90,
    `type` = '0',
    `redirect` = NULL,
    `icon` = 'Document',
    `outer_status` = 0,
    `application_key` = 'dev',
    `deleted` = 0
WHERE `id` = 390;

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

-- 2) 删除可能残留的兼容别名
DELETE FROM `sys_menu`
WHERE `deleted` = 0
  AND `id` <> 260
  AND `path` = '/system/settings';

DELETE FROM `sys_menu`
WHERE `deleted` = 0
  AND `id` <> 300
  AND `component` = 'platform-management';

DELETE FROM `sys_menu`
WHERE `deleted` = 0
  AND `path` IN ('/dashboard/code', '/code/gen');
