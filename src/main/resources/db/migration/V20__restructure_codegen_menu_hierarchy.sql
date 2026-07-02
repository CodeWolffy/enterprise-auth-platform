-- ----------------------------------------------------------------------------
-- V20: 代码生成菜单层级重构
--
-- 目标结构（参考 haorong-mall 项目）：
--   平台管理 (id=300)
--     └── 代码生成 (id=390, 目录, 无 component, redirect → gen-table)
--           ├── 数据源管理 (id=397, component=gen/datasource/index)
--           ├── 数据表管理 (id=398, component=gen/gen-table/index)  [新增]
--           ├── 数据源权限 (id=399~403) [新增]
--           └── 数据表权限 (id=404~408) [新增]
--
-- 变更摘要：
--   1. id=390 代码生成 → 改为纯目录（component=NULL, redirect=/platform/codegen/gen-table）
--   2. id=397 数据源管理 → parent_id 从 300 改为 390，sort=10
--   3. 新增 id=398 数据表管理页面菜单
--   4. 新增数据源管理按钮权限 (id=399~403)
--   5. 新增数据表管理按钮权限 (id=404~408)
--   6. 为新菜单补齐 role_menu / tenant_menu 授权
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';
SET NAMES utf8mb4;

-- ============================
-- 1. 代码生成(390) → 纯目录
-- ============================
UPDATE `sys_menu`
SET
    `component` = NULL,
    `redirect`  = '/platform/codegen/gen-table',
    `icon`      = 'carbon:ibm-cloud-code-engine',
    `sort`      = 90
WHERE `id` = 390;

-- ============================
-- 2. 数据源管理(397) → 归入代码生成下
-- ============================
UPDATE `sys_menu`
SET
    `parent_id` = 390,
    `path`      = '/platform/codegen/datasource',
    `sort`      = 10
WHERE `id` = 397;

-- ============================
-- 3. 新增 数据表管理 页面菜单 (id=398)
-- ============================
INSERT INTO `sys_menu` (
    `id`, `parent_id`, `name`, `permission`, `path`, `component`,
    `sort`, `type`, `redirect`, `icon`, `outer_status`, `application_key`,
    `created_by`, `updated_by`, `deleted`
)
SELECT
    398, 390, '数据表管理', NULL, '/platform/codegen/gen-table', 'gen/gen-table/index',
    20, '0', NULL, 'carbon:data-table', 0, 'dev',
    'system', 'system', 0
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE `id` = 398
);

-- ============================
-- 4. 新增 数据源管理 按钮权限 (id=399,409,401~403)
--    id=400 已被"工作流"占用，使用 409 替代
-- ============================
INSERT IGNORE INTO `sys_menu` (
    `id`, `parent_id`, `name`, `permission`, `path`, `component`,
    `sort`, `type`, `redirect`, `icon`, `outer_status`, `application_key`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    (399, 397, '数据源列表', 'gen:datasource:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'dev', 'system', 'system', 0),
    (409, 397, '数据源查询', 'gen:datasource:get',  NULL, NULL, 2, '1', NULL, NULL, 0, 'dev', 'system', 'system', 0),
    (401, 397, '数据源新增', 'gen:datasource:add',  NULL, NULL, 3, '1', NULL, NULL, 0, 'dev', 'system', 'system', 0),
    (402, 397, '数据源修改', 'gen:datasource:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'dev', 'system', 'system', 0),
    (403, 397, '数据源删除', 'gen:datasource:del',  NULL, NULL, 5, '1', NULL, NULL, 0, 'dev', 'system', 'system', 0);

-- ============================
-- 5. 新增 数据表管理 按钮权限 (id=404~408)
-- ============================
INSERT IGNORE INTO `sys_menu` (
    `id`, `parent_id`, `name`, `permission`, `path`, `component`,
    `sort`, `type`, `redirect`, `icon`, `outer_status`, `application_key`,
    `created_by`, `updated_by`, `deleted`
)
VALUES
    (404, 398, '数据表列表',   'gen:gen-table:page',     NULL, NULL, 1, '1', NULL, NULL, 0, 'dev', 'system', 'system', 0),
    (405, 398, '数据表查询',   'gen:gen-table:get',      NULL, NULL, 2, '1', NULL, NULL, 0, 'dev', 'system', 'system', 0),
    (406, 398, '数据表导入',   'gen:gen-table:add',      NULL, NULL, 3, '1', NULL, NULL, 0, 'dev', 'system', 'system', 0),
    (407, 398, '数据表修改',   'gen:gen-table:edit',     NULL, NULL, 4, '1', NULL, NULL, 0, 'dev', 'system', 'system', 0),
    (408, 398, '代码生成下载', 'gen:gen-table:download', NULL, NULL, 5, '1', NULL, NULL, 0, 'dev', 'system', 'system', 0);

-- ============================
-- 6. 为新菜单(398, 399~408) 补充平台超管角色授权
-- ============================
INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`, `created_at`)
SELECT 'platform', 1, n.id, UTC_TIMESTAMP()
FROM (
    SELECT 398 AS id UNION ALL SELECT 399 UNION ALL SELECT 409 UNION ALL
    SELECT 401 UNION ALL SELECT 402 UNION ALL SELECT 403 UNION ALL
    SELECT 404 UNION ALL SELECT 405 UNION ALL SELECT 406 UNION ALL
    SELECT 407 UNION ALL SELECT 408
) AS n
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_role_menu`
    WHERE `tenant_id` = 'platform' AND `role_id` = 1 AND `menu_id` = n.id
);

-- ============================
-- 7. 为新菜单(398, 399~408) 补充 platform 租户菜单授权
-- ============================
INSERT INTO `sys_tenant_menu` (`tenant_id`, `menu_id`, `created_by`, `created_at`)
SELECT 'platform', n.id, 'system', UTC_TIMESTAMP()
FROM (
    SELECT 398 AS id UNION ALL SELECT 399 UNION ALL SELECT 409 UNION ALL
    SELECT 401 UNION ALL SELECT 402 UNION ALL SELECT 403 UNION ALL
    SELECT 404 UNION ALL SELECT 405 UNION ALL SELECT 406 UNION ALL
    SELECT 407 UNION ALL SELECT 408
) AS n
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_tenant_menu`
    WHERE `tenant_id` = 'platform' AND `menu_id` = n.id
);

-- ============================
-- 8. 为非 platform 租户按 app_key=dev 批量授权
-- ============================
INSERT INTO `sys_tenant_menu` (`tenant_id`, `menu_id`, `created_by`, `created_at`)
SELECT t.`tenant_id`, n.id, 'system', UTC_TIMESTAMP()
FROM `sys_tenant` t
JOIN `sys_tenant_package` p
  ON p.`tenant_id` = 'platform'
 AND p.`package_code` = t.`package_code`
 AND p.`deleted` = 0
CROSS JOIN (
    SELECT 398 AS id UNION ALL SELECT 399 UNION ALL SELECT 409 UNION ALL
    SELECT 401 UNION ALL SELECT 402 UNION ALL SELECT 403 UNION ALL
    SELECT 404 UNION ALL SELECT 405 UNION ALL SELECT 406 UNION ALL
    SELECT 407 UNION ALL SELECT 408
) AS n
WHERE t.`deleted` = 0
  AND t.`tenant_id` <> 'platform'
  AND FIND_IN_SET('dev',
        REPLACE(REPLACE(REPLACE(TRIM(COALESCE(p.`app_key`, '')), ';', ','), ' ', ','), CHAR(9), ',')
      ) > 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_tenant_menu` tm
      WHERE tm.`tenant_id` = t.`tenant_id` AND tm.`menu_id` = n.id
  );