-- 租户套餐应用标识字典
-- 用于 tenant-package 表单 appKey 多选

INSERT INTO sys_dict (tenant_id, dict_type, dict_code, dict_value, description, enabled, remarks, created_by, updated_by, deleted)
VALUES ('platform', 'tenant_package_app_key', 'tenant_package_app_key', '应用标识', '租户套餐应用标识', 1, '平台全局字典', 'system', 'system', 0);

INSERT INTO sys_dict_value (tenant_id, dict_id, dict_type, dict_label, dict_value, show_class, sort, enabled, remarks, created_by, updated_by, deleted) VALUES
('platform', 4, 'tenant_package_app_key', '基础底座', 'base', 'primary', 1, 1, NULL, 'system', 'system', 0),
('platform', 4, 'tenant_package_app_key', '系统管理', 'system', 'primary', 2, 1, NULL, 'system', 'system', 0),
('platform', 4, 'tenant_package_app_key', '平台管理', 'platform', 'primary', 3, 1, NULL, 'system', 'system', 0),
('platform', 4, 'tenant_package_app_key', '工作流', 'workflow', 'primary', 4, 1, NULL, 'system', 'system', 0),
('platform', 4, 'tenant_package_app_key', '开发工具', 'dev', 'primary', 5, 1, NULL, 'system', 'system', 0);