-- Keep dictionary/config category rules useful out of the box.
INSERT INTO `sys_category_rule` (
  `tenant_id`,
  `target_type`,
  `category_code`,
  `category_name`,
  `matchers`,
  `created_by`,
  `updated_by`,
  `deleted`
) VALUES
  ('platform', 'dict', 'system', '系统字典', 'sys_*', 'system', 'system', 0),
  ('platform', 'dict', 'menu', '菜单字典', 'menu_*', 'system', 'system', 0),
  ('platform', 'dict', 'tenant', '租户字典', 'tenant_*,tenant_package_*', 'system', 'system', 0),
  ('platform', 'config', 'registration', '注册参数', 'registration.*', 'system', 'system', 0),
  ('platform', 'config', 'system', '系统参数', 'system.*', 'system', 'system', 0)
ON DUPLICATE KEY UPDATE
  `category_name` = VALUES(`category_name`),
  `matchers` = VALUES(`matchers`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = 0;
