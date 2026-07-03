-- ----------------------------------------------------------------------------
-- V25: Repair critical seed and local development data alignment.
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';
SET NAMES utf8mb4;

-- tenant_package_app_key values may drift when the dictionary header is recreated.
UPDATE `sys_dict_value` dv
JOIN `sys_dict` d
  ON d.`tenant_id` = dv.`tenant_id`
 AND d.`dict_type` = dv.`dict_type`
 AND d.`deleted` = 0
SET dv.`dict_id` = d.`id`,
    dv.`updated_by` = 'system'
WHERE dv.`deleted` = 0
  AND dv.`dict_type` = 'tenant_package_app_key'
  AND dv.`dict_id` <> d.`id`;

-- Align codegen table-management delete permission with the frontend and API.
INSERT INTO `sys_menu` (
    `parent_id`, `name`, `permission`, `path`, `component`,
    `sort`, `type`, `redirect`, `icon`, `outer_status`, `application_key`,
    `created_by`, `updated_by`, `deleted`
)
SELECT
    398, '数据表删除', 'gen:gen-table:del', NULL, NULL,
    6, '1', NULL, NULL, 0, 'dev',
    'system', 'system', 0
WHERE EXISTS (
    SELECT 1 FROM `sys_menu` parent WHERE parent.`id` = 398 AND parent.`deleted` = 0
)
  AND NOT EXISTS (
    SELECT 1 FROM `sys_menu` existing
    WHERE existing.`permission` = 'gen:gen-table:del'
      AND existing.`deleted` = 0
);

INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`, `created_at`)
SELECT 'platform', 1, m.`id`, UTC_TIMESTAMP()
FROM `sys_menu` m
WHERE m.`permission` = 'gen:gen-table:del'
  AND m.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.`tenant_id` = 'platform'
        AND rm.`role_id` = 1
        AND rm.`menu_id` = m.`id`
  );

INSERT INTO `sys_tenant_menu` (`tenant_id`, `menu_id`, `created_by`, `created_at`)
SELECT 'platform', m.`id`, 'system', UTC_TIMESTAMP()
FROM `sys_menu` m
WHERE m.`permission` = 'gen:gen-table:del'
  AND m.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_tenant_menu` tm
      WHERE tm.`tenant_id` = 'platform'
        AND tm.`menu_id` = m.`id`
  );

INSERT INTO `sys_tenant_menu` (`tenant_id`, `menu_id`, `created_by`, `created_at`)
SELECT t.`tenant_id`, m.`id`, 'system', UTC_TIMESTAMP()
FROM `sys_tenant` t
JOIN `sys_tenant_package` p
  ON p.`tenant_id` = 'platform'
 AND p.`package_code` = t.`package_code`
 AND p.`deleted` = 0
JOIN `sys_menu` m
  ON m.`permission` = 'gen:gen-table:del'
 AND m.`deleted` = 0
WHERE t.`deleted` = 0
  AND t.`tenant_id` <> 'platform'
  AND FIND_IN_SET('dev',
        REPLACE(REPLACE(REPLACE(TRIM(COALESCE(p.`app_key`, '')), ';', ','), ' ', ','), CHAR(9), ',')
      ) > 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_tenant_menu` tm
      WHERE tm.`tenant_id` = t.`tenant_id`
        AND tm.`menu_id` = m.`id`
  );

-- Codegen-created placeholder directories must not point at unmapped frontend components.
UPDATE `sys_menu`
SET `component` = NULL,
    `updated_by` = 'system'
WHERE `path` = '/platform/generated'
  AND `deleted` = 0
  AND `component` = 'Layout';

UPDATE `sys_menu` parent
LEFT JOIN `sys_menu` child
  ON child.`parent_id` = parent.`id`
 AND child.`deleted` = 0
SET parent.`deleted` = 1,
    parent.`updated_by` = 'system'
WHERE parent.`path` = '/platform/generated'
  AND parent.`deleted` = 0
  AND child.`id` IS NULL;

-- Remove grants that point to deleted or missing menu/role rows.
DELETE rm
FROM `sys_role_menu` rm
LEFT JOIN `sys_role` r
  ON r.`id` = rm.`role_id`
 AND r.`tenant_id` = rm.`tenant_id`
 AND r.`deleted` = 0
LEFT JOIN `sys_menu` m
  ON m.`id` = rm.`menu_id`
 AND m.`deleted` = 0
WHERE r.`id` IS NULL
   OR m.`id` IS NULL;

DELETE tm
FROM `sys_tenant_menu` tm
LEFT JOIN `sys_menu` m
  ON m.`id` = tm.`menu_id`
 AND m.`deleted` = 0
WHERE m.`id` IS NULL;

-- Keep platform tenant-menu assignments aligned with the active template menu set.
INSERT INTO `sys_tenant_menu` (`tenant_id`, `menu_id`, `created_by`, `created_at`)
SELECT 'platform', m.`id`, 'system', UTC_TIMESTAMP()
FROM `sys_menu` m
WHERE m.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_tenant_menu` tm
      WHERE tm.`tenant_id` = 'platform'
        AND tm.`menu_id` = m.`id`
  );

-- Local tenant-a has historically been used as a development tenant. Repair it only if present.
INSERT INTO `sys_tenant_package` (
    `tenant_id`, `package_code`, `package_name`, `subtitle`,
    `sales_price`, `original_price`, `description_md`, `app_key`,
    `order_no`, `package_desc`, `status`, `created_by`, `updated_by`, `deleted`
)
SELECT
    'platform', 'business-standard', '业务标准版', '本机租户兼容套餐',
    0.00, 0.00, '兼容已有业务标准版租户数据。', 'base,system,platform,workflow',
    20, '业务标准版菜单范围', '0', 'system', 'system', 0
WHERE EXISTS (
    SELECT 1 FROM `sys_tenant`
    WHERE `tenant_id` = 'tenant-a'
      AND `package_code` = 'business-standard'
      AND `deleted` = 0
)
ON DUPLICATE KEY UPDATE
    `package_name` = VALUES(`package_name`),
    `subtitle` = VALUES(`subtitle`),
    `description_md` = VALUES(`description_md`),
    `app_key` = VALUES(`app_key`),
    `package_desc` = VALUES(`package_desc`),
    `status` = VALUES(`status`),
    `updated_by` = 'system',
    `deleted` = 0;

-- Add package-scoped menu grants for active tenants, including menu ancestors.
INSERT INTO `sys_tenant_menu` (`tenant_id`, `menu_id`, `created_by`, `created_at`)
WITH RECURSIVE `package_menu_scope` AS (
    SELECT
        t.`tenant_id`,
        m.`id` AS `menu_id`,
        m.`parent_id`
    FROM `sys_tenant` t
    JOIN `sys_tenant_package` p
      ON p.`tenant_id` = 'platform'
     AND p.`package_code` = t.`package_code`
     AND p.`deleted` = 0
     AND p.`status` = '0'
    JOIN `sys_menu` m
      ON m.`deleted` = 0
     AND FIND_IN_SET(m.`application_key`,
           REPLACE(REPLACE(REPLACE(TRIM(COALESCE(p.`app_key`, '')), ';', ','), ' ', ','), CHAR(9), ',')
         ) > 0
    WHERE t.`deleted` = 0
      AND t.`tenant_id` <> 'platform'
    UNION DISTINCT
    SELECT
        scope.`tenant_id`,
        parent.`id` AS `menu_id`,
        parent.`parent_id`
    FROM `package_menu_scope` scope
    JOIN `sys_menu` parent
      ON parent.`id` = scope.`parent_id`
     AND parent.`deleted` = 0
)
SELECT scope.`tenant_id`, scope.`menu_id`, 'system', UTC_TIMESTAMP()
FROM `package_menu_scope` scope
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_tenant_menu` tm
    WHERE tm.`tenant_id` = scope.`tenant_id`
      AND tm.`menu_id` = scope.`menu_id`
);

INSERT INTO `sys_dept` (
    `tenant_id`, `parent_id`, `dept_name`, `dept_code`,
    `leader_name`, `leader_phone`, `order_no`, `enabled`,
    `created_by`, `updated_by`, `deleted`
)
SELECT
    t.`tenant_id`, NULL, CONCAT(t.`tenant_name`, '总部'), 'ROOT',
    NULL, NULL, 0, 1, 'system', 'system', 0
FROM `sys_tenant` t
WHERE t.`tenant_id` = 'tenant-a'
  AND t.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_dept` d
      WHERE d.`tenant_id` = t.`tenant_id`
        AND d.`dept_code` = 'ROOT'
        AND d.`deleted` = 0
  );

UPDATE `sys_user` u
JOIN `sys_dept` root
  ON root.`tenant_id` = u.`tenant_id`
 AND root.`dept_code` = 'ROOT'
 AND root.`deleted` = 0
LEFT JOIN `sys_dept` current_dept
  ON current_dept.`id` = u.`dept_id`
 AND current_dept.`tenant_id` = u.`tenant_id`
 AND current_dept.`deleted` = 0
SET u.`dept_id` = root.`id`,
    u.`updated_by` = 'system'
WHERE u.`tenant_id` = 'tenant-a'
  AND u.`deleted` = 0
  AND u.`dept_id` IS NOT NULL
  AND current_dept.`id` IS NULL;

INSERT INTO `sys_role` (
    `tenant_id`, `role_code`, `role_name`, `data_scope_type`,
    `role_desc`, `data_scope_value_json`, `created_by`, `updated_by`, `deleted`
)
SELECT 'tenant-a', 'TENANT_ADMIN', '租户管理员', 'ALL',
       '租户数据修复补齐的管理员角色', NULL, 'system', 'system', 0
WHERE EXISTS (
    SELECT 1 FROM `sys_tenant` WHERE `tenant_id` = 'tenant-a' AND `deleted` = 0
)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `data_scope_type` = VALUES(`data_scope_type`),
    `role_desc` = VALUES(`role_desc`),
    `updated_by` = 'system',
    `deleted` = 0;

INSERT INTO `sys_role` (
    `tenant_id`, `role_code`, `role_name`, `data_scope_type`,
    `role_desc`, `data_scope_value_json`, `created_by`, `updated_by`, `deleted`
)
SELECT 'tenant-a', 'USER', '普通用户', 'SELF',
       '租户数据修复补齐的普通用户角色', NULL, 'system', 'system', 0
WHERE EXISTS (
    SELECT 1 FROM `sys_tenant` WHERE `tenant_id` = 'tenant-a' AND `deleted` = 0
)
ON DUPLICATE KEY UPDATE
    `role_name` = VALUES(`role_name`),
    `data_scope_type` = VALUES(`data_scope_type`),
    `role_desc` = VALUES(`role_desc`),
    `updated_by` = 'system',
    `deleted` = 0;

DELETE ur
FROM `sys_user_role` ur
LEFT JOIN `sys_user` u
  ON u.`id` = ur.`user_id`
 AND u.`tenant_id` = ur.`tenant_id`
 AND u.`deleted` = 0
LEFT JOIN `sys_role` r
  ON r.`id` = ur.`role_id`
 AND r.`tenant_id` = ur.`tenant_id`
 AND r.`deleted` = 0
WHERE u.`id` IS NULL
   OR r.`id` IS NULL;

INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`, `created_at`)
SELECT 'tenant-a', r.`id`, tm.`menu_id`, UTC_TIMESTAMP()
FROM `sys_role` r
JOIN `sys_tenant_menu` tm
  ON tm.`tenant_id` = 'tenant-a'
JOIN `sys_menu` m
  ON m.`id` = tm.`menu_id`
 AND m.`deleted` = 0
WHERE r.`tenant_id` = 'tenant-a'
  AND r.`role_code` = 'TENANT_ADMIN'
  AND r.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` rm
      WHERE rm.`tenant_id` = 'tenant-a'
        AND rm.`role_id` = r.`id`
        AND rm.`menu_id` = tm.`menu_id`
  );

INSERT INTO `sys_user_role` (`tenant_id`, `user_id`, `role_id`, `created_by`, `updated_by`)
SELECT u.`tenant_id`, u.`id`, r.`id`, 'system', 'system'
FROM `sys_user` u
JOIN `sys_role` r
  ON r.`tenant_id` = u.`tenant_id`
 AND r.`role_code` = 'TENANT_ADMIN'
 AND r.`deleted` = 0
WHERE u.`tenant_id` = 'tenant-a'
  AND u.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `sys_user_role` ur
      JOIN `sys_role` existing_role
        ON existing_role.`id` = ur.`role_id`
       AND existing_role.`tenant_id` = ur.`tenant_id`
       AND existing_role.`deleted` = 0
      WHERE ur.`tenant_id` = u.`tenant_id`
        AND ur.`user_id` = u.`id`
  );
