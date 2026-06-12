-- =====================================================
-- 对齐 haorong-mall 菜单类型和租户菜单分配约束
-- sys_menu.menu_type: 0=菜单, 1=按钮
-- sys_tenant_menu: 同一租户同一菜单只允许一条分配记录
-- =====================================================

UPDATE `sys_menu`
SET `menu_type` = CASE
  WHEN `menu_type` IN ('DIR', 'MENU') THEN '0'
  WHEN `menu_type` IN ('BUTTON', 'API') THEN '1'
  ELSE `menu_type`
END
WHERE `menu_type` IN ('DIR', 'MENU', 'BUTTON', 'API');

DELETE tm
FROM `sys_tenant_menu` tm
JOIN `sys_tenant_menu` keep_tm
  ON keep_tm.`tenant_id` = tm.`tenant_id`
 AND keep_tm.`menu_id` = tm.`menu_id`
 AND keep_tm.`id` < tm.`id`;

SET @tenant_menu_unique_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_tenant_menu'
    AND index_name = 'uk_sys_tenant_menu_tenant_menu'
);

SET @tenant_menu_unique_sql = IF(
  @tenant_menu_unique_exists = 0,
  'ALTER TABLE `sys_tenant_menu` ADD UNIQUE KEY `uk_sys_tenant_menu_tenant_menu` (`tenant_id`, `menu_id`)',
  'SELECT 1'
);

PREPARE tenant_menu_unique_stmt FROM @tenant_menu_unique_sql;
EXECUTE tenant_menu_unique_stmt;
DEALLOCATE PREPARE tenant_menu_unique_stmt;