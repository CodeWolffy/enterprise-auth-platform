ALTER TABLE `sys_mail_channel`
  MODIFY COLUMN `mail_password` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SMTP 密码或授权码密文/原文';

ALTER TABLE `sys_mail_channel`
  DROP INDEX `idx_sys_mail_channel_enabled`,
  ADD INDEX `idx_sys_mail_channel_enabled` (`tenant_id` ASC, `enabled` ASC, `deleted` ASC) USING BTREE;