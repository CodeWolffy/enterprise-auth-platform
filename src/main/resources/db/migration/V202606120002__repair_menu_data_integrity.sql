-- =====================================================
-- 修复菜单实际数据一致性
-- 1. 删除租户菜单中不存在的菜单引用
-- 2. 修复 sys_menu.ancestors 祖先链
-- 3. 统一 sys_tenant_menu 字符集/排序规则，避免 tenant_id join 报错
-- 4. 修正从参考项目遗留的路由路径和组件名
-- =====================================================

DELETE tm
FROM `sys_tenant_menu` tm
LEFT JOIN `sys_menu` m
  ON m.`id` = tm.`menu_id`
 AND m.`deleted` = 0
WHERE m.`id` IS NULL;

DROP TEMPORARY TABLE IF EXISTS `tmp_sys_menu_ancestors`;
CREATE TEMPORARY TABLE `tmp_sys_menu_ancestors` (
  `id` bigint NOT NULL PRIMARY KEY,
  `ancestors` varchar(512) NOT NULL
) ENGINE=Memory;

INSERT INTO `tmp_sys_menu_ancestors` (`id`, `ancestors`)
WITH RECURSIVE menu_tree AS (
  SELECT
    m.`id`,
    m.`parent_id`,
    CAST('' AS CHAR(512)) AS `ancestors`
  FROM `sys_menu` m
  WHERE m.`deleted` = 0
    AND m.`parent_id` IS NULL

  UNION ALL

  SELECT
    c.`id`,
    c.`parent_id`,
    CAST(
      CASE
        WHEN mt.`ancestors` = '' THEN CAST(c.`parent_id` AS CHAR)
        ELSE CONCAT(mt.`ancestors`, ',', c.`parent_id`)
      END AS CHAR(512)
    ) AS `ancestors`
  FROM `sys_menu` c
  JOIN menu_tree mt ON mt.`id` = c.`parent_id`
  WHERE c.`deleted` = 0
)
SELECT `id`, `ancestors`
FROM menu_tree;

UPDATE `sys_menu` m
JOIN `tmp_sys_menu_ancestors` t ON t.`id` = m.`id`
SET m.`ancestors` = t.`ancestors`
WHERE CAST(IFNULL(m.`ancestors`, '') AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci <> t.`ancestors` COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS `tmp_sys_menu_ancestors`;

ALTER TABLE `sys_tenant_menu`
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `sys_tenant_menu`
  MODIFY `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID',
  MODIFY `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人';

UPDATE `sys_menu`
SET `path` = '/platform/tenants',
    `component` = 'TenantsView'
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `resource_key` = 'tenants';

UPDATE `sys_menu`
SET `path` = '/system/menus',
    `component` = 'MenuManagementView'
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `resource_key` = 'menus';

UPDATE `sys_menu`
SET `path` = '/platform/dicts',
    `component` = 'SystemDictsView'
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `resource_key` = 'dicts';

UPDATE `sys_menu`
SET `path` = '/platform/tenant-catalog',
    `component` = 'TenantCatalogView'
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `resource_key` = 'tenant-catalog';

UPDATE `sys_menu`
SET `path` = '/platform/configs',
    `component` = 'SystemConfigsView'
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `resource_key` = 'configs';