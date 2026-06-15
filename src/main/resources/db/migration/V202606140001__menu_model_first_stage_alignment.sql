-- =====================================================
-- 菜单模型第一阶段收敛
-- 1. 清理角色/租户菜单重复授权数据
-- 2. 移除 sys_tenant_menu/sys_role_menu 增强唯一约束，改由服务层保存前去重
-- 3. 新增 haorong-mall 目标字段并从旧字段回填
-- 4. 保留旧列作为兼容窗口，本阶段不物理删除旧列
-- =====================================================

DELETE rm
FROM `sys_role_menu` rm
JOIN `sys_role_menu` keep_rm
  ON keep_rm.`tenant_id` = rm.`tenant_id`
 AND keep_rm.`role_id` = rm.`role_id`
 AND keep_rm.`menu_id` = rm.`menu_id`
 AND keep_rm.`id` < rm.`id`;

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

SET @tenant_menu_drop_unique_sql = IF(
  @tenant_menu_unique_exists > 0,
  'ALTER TABLE `sys_tenant_menu` DROP INDEX `uk_sys_tenant_menu_tenant_menu`',
  'SELECT 1'
);

PREPARE tenant_menu_drop_unique_stmt FROM @tenant_menu_drop_unique_sql;
EXECUTE tenant_menu_drop_unique_stmt;
DEALLOCATE PREPARE tenant_menu_drop_unique_stmt;

SET @role_menu_unique_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_role_menu'
    AND index_name = 'uk_sys_role_menu_tenant_role_menu'
);

SET @role_menu_drop_unique_sql = IF(
  @role_menu_unique_exists > 0,
  'ALTER TABLE `sys_role_menu` DROP INDEX `uk_sys_role_menu_tenant_role_menu`',
  'SELECT 1'
);

PREPARE role_menu_drop_unique_stmt FROM @role_menu_drop_unique_sql;
EXECUTE role_menu_drop_unique_stmt;
DEALLOCATE PREPARE role_menu_drop_unique_stmt;

SET @menu_name_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_menu'
    AND column_name = 'name'
);
SET @menu_add_name_sql = IF(
  @menu_name_exists = 0,
  'ALTER TABLE `sys_menu` ADD COLUMN `name` varchar(60) DEFAULT NULL COMMENT ''菜单名称'' AFTER `resource_key`',
  'SELECT 1'
);
PREPARE menu_add_name_stmt FROM @menu_add_name_sql;
EXECUTE menu_add_name_stmt;
DEALLOCATE PREPARE menu_add_name_stmt;

SET @menu_permission_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_menu'
    AND column_name = 'permission'
);
SET @menu_add_permission_sql = IF(
  @menu_permission_exists = 0,
  'ALTER TABLE `sys_menu` ADD COLUMN `permission` varchar(128) DEFAULT NULL COMMENT ''菜单权限'' AFTER `name`',
  'SELECT 1'
);
PREPARE menu_add_permission_stmt FROM @menu_add_permission_sql;
EXECUTE menu_add_permission_stmt;
DEALLOCATE PREPARE menu_add_permission_stmt;

SET @menu_sort_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_menu'
    AND column_name = 'sort'
);
SET @menu_add_sort_sql = IF(
  @menu_sort_exists = 0,
  'ALTER TABLE `sys_menu` ADD COLUMN `sort` int NOT NULL DEFAULT 0 COMMENT ''排序'' AFTER `component`',
  'SELECT 1'
);
PREPARE menu_add_sort_stmt FROM @menu_add_sort_sql;
EXECUTE menu_add_sort_stmt;
DEALLOCATE PREPARE menu_add_sort_stmt;

SET @menu_type_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_menu'
    AND column_name = 'type'
);
SET @menu_add_type_sql = IF(
  @menu_type_exists = 0,
  'ALTER TABLE `sys_menu` ADD COLUMN `type` char(1) DEFAULT NULL COMMENT ''类型：0=菜单；1=按钮'' AFTER `sort`',
  'SELECT 1'
);
PREPARE menu_add_type_stmt FROM @menu_add_type_sql;
EXECUTE menu_add_type_stmt;
DEALLOCATE PREPARE menu_add_type_stmt;

SET @menu_del_flag_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_menu'
    AND column_name = 'del_flag'
);
SET @menu_add_del_flag_sql = IF(
  @menu_del_flag_exists = 0,
  'ALTER TABLE `sys_menu` ADD COLUMN `del_flag` char(1) NOT NULL DEFAULT ''0'' COMMENT ''逻辑删除：0=正常；1=删除'' AFTER `application_key`',
  'SELECT 1'
);
PREPARE menu_add_del_flag_stmt FROM @menu_add_del_flag_sql;
EXECUTE menu_add_del_flag_stmt;
DEALLOCATE PREPARE menu_add_del_flag_stmt;

UPDATE `sys_menu`
SET `name` = COALESCE(NULLIF(`name`, ''), `menu_name`),
    `permission` = COALESCE(NULLIF(`permission`, ''), `grant_key`),
    `sort` = COALESCE(`sort`, `order_no`, 0),
    `type` = COALESCE(NULLIF(`type`, ''), `menu_type`),
    `del_flag` = CASE WHEN `deleted` = 1 THEN '1' ELSE '0' END
WHERE `tenant_id` = 'platform';

SET @idx_menu_parent_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_menu'
    AND index_name = 'idx_sys_menu_parent'
);
SET @idx_menu_parent_sql = IF(
  @idx_menu_parent_exists = 0,
  'ALTER TABLE `sys_menu` ADD INDEX `idx_sys_menu_parent` (`parent_id`)',
  'SELECT 1'
);
PREPARE idx_menu_parent_stmt FROM @idx_menu_parent_sql;
EXECUTE idx_menu_parent_stmt;
DEALLOCATE PREPARE idx_menu_parent_stmt;

SET @idx_menu_type_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_menu'
    AND index_name = 'idx_sys_menu_type'
);
SET @idx_menu_type_sql = IF(
  @idx_menu_type_exists = 0,
  'ALTER TABLE `sys_menu` ADD INDEX `idx_sys_menu_type` (`type`)',
  'SELECT 1'
);
PREPARE idx_menu_type_stmt FROM @idx_menu_type_sql;
EXECUTE idx_menu_type_stmt;
DEALLOCATE PREPARE idx_menu_type_stmt;

SET @idx_menu_permission_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_menu'
    AND index_name = 'idx_sys_menu_permission'
);
SET @idx_menu_permission_sql = IF(
  @idx_menu_permission_exists = 0,
  'ALTER TABLE `sys_menu` ADD INDEX `idx_sys_menu_permission` (`permission`)',
  'SELECT 1'
);
PREPARE idx_menu_permission_stmt FROM @idx_menu_permission_sql;
EXECUTE idx_menu_permission_stmt;
DEALLOCATE PREPARE idx_menu_permission_stmt;

SET @idx_menu_del_flag_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_menu'
    AND index_name = 'idx_sys_menu_del_flag'
);
SET @idx_menu_del_flag_sql = IF(
  @idx_menu_del_flag_exists = 0,
  'ALTER TABLE `sys_menu` ADD INDEX `idx_sys_menu_del_flag` (`del_flag`)',
  'SELECT 1'
);
PREPARE idx_menu_del_flag_stmt FROM @idx_menu_del_flag_sql;
EXECUTE idx_menu_del_flag_stmt;
DEALLOCATE PREPARE idx_menu_del_flag_stmt;

SET @idx_menu_application_key_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_menu'
    AND index_name = 'idx_sys_menu_application_key'
);
SET @idx_menu_application_key_sql = IF(
  @idx_menu_application_key_exists = 0,
  'ALTER TABLE `sys_menu` ADD INDEX `idx_sys_menu_application_key` (`application_key`)',
  'SELECT 1'
);
PREPARE idx_menu_application_key_stmt FROM @idx_menu_application_key_sql;
EXECUTE idx_menu_application_key_stmt;
DEALLOCATE PREPARE idx_menu_application_key_stmt;

ALTER TABLE `sys_role_menu`
  CHANGE COLUMN `created_at` `create_time` datetime DEFAULT NULL COMMENT '创建时间';

SET @role_menu_created_by_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'sys_role_menu'
    AND column_name = 'created_by'
);
SET @role_menu_drop_created_by_sql = IF(
  @role_menu_created_by_exists > 0,
  'ALTER TABLE `sys_role_menu` DROP COLUMN `created_by`',
  'SELECT 1'
);
PREPARE role_menu_drop_created_by_stmt FROM @role_menu_drop_created_by_sql;
EXECUTE role_menu_drop_created_by_stmt;
DEALLOCATE PREPARE role_menu_drop_created_by_stmt;

ALTER TABLE `sys_tenant_menu`
  CHANGE COLUMN `created_at` `create_time` datetime DEFAULT NULL COMMENT '创建时间';

ALTER TABLE `sys_tenant_menu`
  CHANGE COLUMN `created_by` `create_by` varchar(64) DEFAULT NULL COMMENT '创建人';

ALTER TABLE `sys_menu`
  DROP COLUMN `tenant_id`,
  DROP COLUMN `ancestors`,
  DROP COLUMN `menu_type`,
  DROP COLUMN `resource_key`,
  DROP COLUMN `menu_name`,
  DROP COLUMN `route_key`,
  DROP COLUMN `grant_key`,
  DROP COLUMN `order_no`,
  DROP COLUMN `visible`,
  DROP COLUMN `enabled`,
  DROP COLUMN `is_system`;