-- =====================================================
-- 删除旧资源兼容表
-- sys_menu + sys_role_menu 为唯一权限主链路
-- 覆盖已执行 V202606070006 后的归档表，以及未归档环境中的旧表
-- =====================================================

DROP TABLE IF EXISTS `sys_tenant_resource_override`;
DROP TABLE IF EXISTS `sys_role_resource_archive_20260607`;
DROP TABLE IF EXISTS `sys_resource_archive_20260607`;
DROP TABLE IF EXISTS `sys_role_resource`;
DROP TABLE IF EXISTS `sys_resource`;