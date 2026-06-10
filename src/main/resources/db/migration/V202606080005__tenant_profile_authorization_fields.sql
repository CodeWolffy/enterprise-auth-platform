-- =====================================================
-- P3 租户主档资料与授权期限字段补齐
-- =====================================================

ALTER TABLE `sys_tenant`
  ADD COLUMN `auth_begin_at` datetime NULL DEFAULT NULL COMMENT '授权开始时间' AFTER `tenant_status`,
  ADD COLUMN `logo_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '租户 Logo 地址' AFTER `package_code`,
  ADD COLUMN `contact_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人姓名' AFTER `logo_url`,
  ADD COLUMN `contact_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人电话' AFTER `contact_name`,
  ADD COLUMN `contact_email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人邮箱' AFTER `contact_phone`,
  ADD COLUMN `website` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '官网地址' AFTER `contact_email`,
  ADD COLUMN `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系地址' AFTER `website`;

UPDATE `sys_tenant`
SET `auth_begin_at` = COALESCE(`auth_begin_at`, `created_at`)
WHERE `deleted` = 0;

CREATE INDEX `idx_sys_tenant_auth_window`
  ON `sys_tenant` (`tenant_id`, `deleted`, `tenant_status`, `auth_begin_at`, `expire_at`);