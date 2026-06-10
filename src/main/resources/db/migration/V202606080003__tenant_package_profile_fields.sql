-- =====================================================
-- P2 租户套餐运营展示字段补齐
-- =====================================================

ALTER TABLE `sys_tenant_package`
  ADD COLUMN `subtitle` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '运营副标题' AFTER `package_name`,
  ADD COLUMN `sales_price` decimal(12,2) NULL DEFAULT NULL COMMENT '销售价' AFTER `subtitle`,
  ADD COLUMN `original_price` decimal(12,2) NULL DEFAULT NULL COMMENT '原价' AFTER `sales_price`,
  ADD COLUMN `description_md` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '富文本或 Markdown 描述' AFTER `original_price`,
  ADD COLUMN `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '应用标识' AFTER `description_md`,
  ADD COLUMN `order_no` int NOT NULL DEFAULT 0 COMMENT '展示排序' AFTER `app_key`;

UPDATE `sys_tenant_package`
SET `subtitle` = CASE
    WHEN `package_code` = 'platform-governance' THEN '平台治理与全局运维'
    WHEN `package_code` = 'business-standard' THEN '标准业务租户套餐'
    ELSE `package_name`
  END,
  `description_md` = `package_desc`,
  `app_key` = CASE
    WHEN `package_code` = 'platform-governance' THEN 'app_platform'
    WHEN `package_code` = 'business-standard' THEN 'app_base'
    ELSE `package_code`
  END,
  `order_no` = CASE
    WHEN `package_code` = 'platform-governance' THEN 10
    WHEN `package_code` = 'business-standard' THEN 20
    ELSE 100
  END
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0;

CREATE INDEX `idx_sys_tenant_package_tenant_enabled_order`
  ON `sys_tenant_package` (`tenant_id`, `deleted`, `enabled`, `order_no`);