-- ----------------------------------------------------------------------------
-- V19：代码生成白名单种子数据
--
-- 将核心业务表加入平台租户的代码生成白名单，
-- 使 /api/codegen/tables 能够列出这些表供用户选择和生成。
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';
SET NAMES utf8mb4;

INSERT INTO `sys_codegen_allowlist` (`tenant_id`, `table_name`, `description`, `enabled`, `created_by`, `updated_by`)
SELECT 'platform', t.`table_name`, CONCAT('系统表 - ', t.`table_name`), 1, 'system', 'system'
FROM (
    SELECT 'sys_user' AS `table_name`
    UNION ALL SELECT 'sys_role'
    UNION ALL SELECT 'sys_dept'
    UNION ALL SELECT 'sys_menu'
    UNION ALL SELECT 'sys_dict'
    UNION ALL SELECT 'sys_dict_value'
    UNION ALL SELECT 'sys_config'
    UNION ALL SELECT 'sys_notice'
    UNION ALL SELECT 'sys_log'
    UNION ALL SELECT 'sys_login_log'
    UNION ALL SELECT 'sys_mail_channel'
    UNION ALL SELECT 'sys_tenant'
    UNION ALL SELECT 'sys_tenant_package'
    UNION ALL SELECT 'sys_storage_file'
    UNION ALL SELECT 'sys_category_rule'
    UNION ALL SELECT 'wf_process_definition'
    UNION ALL SELECT 'wf_process_instance'
    UNION ALL SELECT 'wf_task'
    UNION ALL SELECT 'wf_task_urge'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_codegen_allowlist` a
    WHERE a.`tenant_id` = 'platform' AND a.`table_name` = t.`table_name` AND a.`deleted` = 0
);