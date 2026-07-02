-- ----------------------------------------------------------------------------
-- V11: 补齐代码生成 - 数据源管理 菜单
--
-- 说明：
--   * frontend-vben 已有 /views/gen/datasource 页面，但 sys_menu 里没有对应入口
--   * 该菜单沿用现有代码生成权限码：upms:codegen:page/get/add/edit/del
--   * 同步补进平台超级管理员与平台租户默认菜单授权
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

UPDATE `sys_menu`
SET
    `parent_id` = 390,
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
WHERE `id` = 397
   OR `path` = '/platform/codegen/datasource';

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `name`, `permission`, `path`, `component`, `sort`,
    `type`, `redirect`, `icon`, `outer_status`, `application_key`,
    `created_by`, `updated_by`, `deleted`
)
SELECT
    397, 390, '数据源管理', NULL, '/platform/codegen/datasource', 'gen/datasource/index', 95,
    '0', NULL, 'carbon:database', 0, 'dev',
    'system', 'system', 0
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_menu`
    WHERE `id` = 397
       OR `path` = '/platform/codegen/datasource'
);

INSERT INTO `sys_role_menu` (
    `tenant_id`, `role_id`, `menu_id`, `created_at`
)
SELECT
    'platform', 1, 397, UTC_TIMESTAMP()
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu`
    WHERE `tenant_id` = 'platform'
      AND `role_id` = 1
      AND `menu_id` = 397
);

INSERT INTO `sys_tenant_menu` (
    `tenant_id`, `menu_id`, `created_by`, `created_at`
)
SELECT
    t.`tenant_id`, 397, 'system', UTC_TIMESTAMP()
FROM `sys_tenant` t
JOIN `sys_tenant_package` p
  ON p.`tenant_id` = 'platform'
 AND p.`package_code` = t.`package_code`
 AND p.`deleted` = 0
WHERE t.`deleted` = 0
  AND t.`tenant_id` <> 'platform'
  AND TRIM(COALESCE(p.`app_key`, '')) <> ''
  AND FIND_IN_SET(
        'dev',
        REPLACE(
            REPLACE(
                REPLACE(TRIM(COALESCE(p.`app_key`, '')), ';', ','),
                ' ',
                ','
            ),
            CHAR(9),
            ','
        )
      ) > 0
  AND NOT EXISTS (
      SELECT 1
      FROM `sys_tenant_menu` tm
      WHERE tm.`tenant_id` = t.`tenant_id`
        AND tm.`menu_id` = 397
  );

INSERT INTO `sys_tenant_menu` (
    `tenant_id`, `menu_id`, `created_by`, `created_at`
)
SELECT
    'platform', 397, 'system', UTC_TIMESTAMP()
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_tenant_menu`
    WHERE `tenant_id` = 'platform'
      AND `menu_id` = 397
);
