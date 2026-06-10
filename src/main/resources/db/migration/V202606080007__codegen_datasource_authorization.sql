ALTER TABLE `codegen_data_source`
  ADD COLUMN `external_authorized` tinyint NOT NULL DEFAULT 0 COMMENT '外部数据源是否已显式授权' AFTER `enabled`,
  ADD COLUMN `authorized_at` timestamp NULL DEFAULT NULL COMMENT '外部数据源授权时间' AFTER `external_authorized`,
  ADD COLUMN `authorization_note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '外部数据源授权说明' AFTER `authorized_at`;

UPDATE `codegen_data_source`
SET `external_authorized` = 1,
    `authorization_note` = COALESCE(`authorization_note`, '当前应用库默认授权')
WHERE `jdbc_url` = 'LOCAL'
  AND `deleted` = 0;