-- =====================================================
-- 下线旧资源授权表
-- sys_menu + sys_role_menu 已成为运行时主链路
-- 旧表数据保留在归档表，必要时可人工按归档表恢复
-- =====================================================

RENAME TABLE `sys_role_resource` TO `sys_role_resource_archive_20260607`;
RENAME TABLE `sys_resource` TO `sys_resource_archive_20260607`;

ALTER TABLE `sys_role_resource_archive_20260607` COMMENT = '旧角色资源关联归档表，运行时不再读写';
ALTER TABLE `sys_resource_archive_20260607` COMMENT = '旧资源归档表，运行时不再读写';