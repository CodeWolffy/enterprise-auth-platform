-- ----------------------------------------------------------------------------
-- V15: 将「租户套餐」菜单切换到新的 Vben 页面
--
-- 说明：
--   * frontend-vben 中已存在 /platform/tenant-catalog 页面
--   * 该页面承接租户套餐管理与影响分析能力
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';
SET NAMES utf8mb4;

UPDATE `sys_menu`
SET
    `parent_id` = 300,
    `name` = '租户套餐',
    `permission` = NULL,
    `path` = '/platform/tenant-catalog',
    `component` = 'system/TenantCatalogView',
    `sort` = 20,
    `type` = '0',
    `redirect` = NULL,
    `icon` = 'carbon:catalog',
    `outer_status` = 0,
    `application_key` = 'platform',
    `deleted` = 0
WHERE `id` = 320
   OR `path` = '/platform/tenant-catalog';

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `name`, `permission`, `path`, `component`, `sort`,
    `type`, `redirect`, `icon`, `outer_status`, `application_key`,
    `created_by`, `updated_by`, `deleted`
)
SELECT
    320, 300, '租户套餐', NULL, '/platform/tenant-catalog', 'system/TenantCatalogView', 20,
    '0', NULL, 'carbon:catalog', 0, 'platform',
    'system', 'system', 0
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_menu`
    WHERE `id` = 320
       OR `path` = '/platform/tenant-catalog'
);

INSERT INTO `sys_role_menu` (
    `tenant_id`, `role_id`, `menu_id`, `created_at`
)
SELECT
    'platform', 1, 320, UTC_TIMESTAMP()
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu`
    WHERE `tenant_id` = 'platform'
      AND `role_id` = 1
      AND `menu_id` = 320
);

INSERT INTO `sys_tenant_menu` (
    `tenant_id`, `menu_id`, `created_by`, `created_at`
)
SELECT
    t.`tenant_id`, 320, 'system', UTC_TIMESTAMP()
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
        AND tm.`menu_id` = 320
  );

INSERT INTO `sys_tenant_menu` (
    `tenant_id`, `menu_id`, `created_by`, `created_at`
)
SELECT
    'platform', 320, 'system', UTC_TIMESTAMP()
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_tenant_menu`
    WHERE `tenant_id` = 'platform'
      AND `menu_id` = 320
);
