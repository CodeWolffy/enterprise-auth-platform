-- ----------------------------------------------------------------------------
-- V13: 补齐「安全策略」菜单入口
--
-- 说明：
--   * frontend-vben 已有 /system/security 页面
--   * 该菜单属于系统管理分组，需同步平台角色与租户菜单授权
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

UPDATE `sys_menu`
SET
    `parent_id` = 200,
    `name` = '安全策略',
    `permission` = NULL,
    `path` = '/system/security',
    `component` = 'system/SecurityPolicyView',
    `sort` = 65,
    `type` = '0',
    `redirect` = NULL,
    `icon` = 'carbon:security',
    `outer_status` = 0,
    `application_key` = 'system',
    `deleted` = 0
WHERE `id` = 265
   OR `path` = '/system/security';

INSERT INTO `sys_menu` (
    `id`, `parent_id`, `name`, `permission`, `path`, `component`, `sort`,
    `type`, `redirect`, `icon`, `outer_status`, `application_key`,
    `created_by`, `updated_by`, `deleted`
)
SELECT
    265, 200, '安全策略', NULL, '/system/security', 'system/SecurityPolicyView', 65,
    '0', NULL, 'carbon:security', 0, 'system',
    'system', 'system', 0
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_menu`
    WHERE `id` = 265
       OR `path` = '/system/security'
);

INSERT INTO `sys_role_menu` (
    `tenant_id`, `role_id`, `menu_id`, `created_at`
)
SELECT
    'platform', 1, 265, UTC_TIMESTAMP()
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_role_menu`
    WHERE `tenant_id` = 'platform'
      AND `role_id` = 1
      AND `menu_id` = 265
);

INSERT INTO `sys_tenant_menu` (
    `tenant_id`, `menu_id`, `created_by`, `created_at`
)
SELECT
    t.`tenant_id`, 265, 'system', UTC_TIMESTAMP()
FROM `sys_tenant` t
JOIN `sys_tenant_package` p
  ON p.`tenant_id` = 'platform'
 AND p.`package_code` = t.`package_code`
 AND p.`deleted` = 0
WHERE t.`deleted` = 0
  AND t.`tenant_id` <> 'platform'
  AND TRIM(COALESCE(p.`app_key`, '')) <> ''
  AND FIND_IN_SET(
        'system',
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
        AND tm.`menu_id` = 265
  );

INSERT INTO `sys_tenant_menu` (
    `tenant_id`, `menu_id`, `created_by`, `created_at`
)
SELECT
    'platform', 265, 'system', UTC_TIMESTAMP()
WHERE NOT EXISTS (
    SELECT 1
    FROM `sys_tenant_menu`
    WHERE `tenant_id` = 'platform'
      AND `menu_id` = 265
);
