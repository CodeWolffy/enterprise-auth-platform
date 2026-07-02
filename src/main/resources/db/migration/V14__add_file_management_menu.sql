-- ----------------------------------------------------------------------------
-- V14: 补齐「文件管理」菜单入口
--
-- 说明：
--   * frontend-vben 已有 /platform/files 页面
--   * 该菜单归属平台管理分组，需同步平台角色与租户菜单授权
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

UPDATE `sys_menu`
SET
    `parent_id` = 300,
    `name` = '文件管理',
    `permission` = NULL,
    `path` = '/platform/files',
    `component` = 'upms/file/index',
    `sort` = 30,
    `type` = '0',
    `redirect` = NULL,
    `icon` = 'carbon:folder',
    `outer_status` = 0,
    `application_key` = 'platform',
    `deleted` = 0
WHERE `id` = 330
   OR `path` = '/platform/files';

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `name`, `permission`, `path`, `component`, `sort`,
    `type`, `redirect`, `icon`, `outer_status`, `application_key`,
    `created_by`, `updated_by`, `deleted`
)
SELECT
    330, 300, '文件管理', NULL, '/platform/files', 'upms/file/index', 30,
    '0', NULL, 'carbon:folder', 0, 'platform',
    'system', 'system', 0
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_menu`
    WHERE `id` = 330
       OR `path` = '/platform/files'
);

INSERT INTO `sys_role_menu` (
    `tenant_id`, `role_id`, `menu_id`, `created_at`
)
SELECT
    'platform', 1, 330, UTC_TIMESTAMP()
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu`
    WHERE `tenant_id` = 'platform'
      AND `role_id` = 1
      AND `menu_id` = 330
);

INSERT INTO `sys_tenant_menu` (
    `tenant_id`, `menu_id`, `created_by`, `created_at`
)
SELECT
    t.`tenant_id`, 330, 'system', UTC_TIMESTAMP()
FROM `sys_tenant` t
JOIN `sys_tenant_package` p
  ON p.`tenant_id` = 'platform'
 AND p.`package_code` = t.`package_code`
 AND p.`deleted` = 0
WHERE t.`deleted` = 0
  AND t.`tenant_id` <> 'platform'
  AND TRIM(COALESCE(p.`app_key`, '')) <> ''
  AND FIND_IN_SET(
        'platform',
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
        AND tm.`menu_id` = 330
  );

INSERT INTO `sys_tenant_menu` (
    `tenant_id`, `menu_id`, `created_by`, `created_at`
)
SELECT
    'platform', 330, 'system', UTC_TIMESTAMP()
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_tenant_menu`
    WHERE `tenant_id` = 'platform'
      AND `menu_id` = 330
);
