-- Audit list/export query indexes.
-- Estimated impact: adds two secondary indexes to sys_audit_log; large tables should schedule during low traffic.
-- Rollback: DROP INDEX idx_sys_audit_log_tenant_event_time ON sys_audit_log;
-- Rollback: DROP INDEX idx_sys_audit_log_tenant_operator_time ON sys_audit_log;

SET @index_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_audit_log'
      AND index_name = 'idx_sys_audit_log_tenant_event_time'
);

SET @ddl := IF(
    @index_exists = 0,
    'ALTER TABLE `sys_audit_log` ADD INDEX `idx_sys_audit_log_tenant_event_time` (`tenant_id`, `event_type`, `occurred_at`, `id`)',
    'SELECT ''idx_sys_audit_log_tenant_event_time already exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_audit_log'
      AND index_name = 'idx_sys_audit_log_tenant_operator_time'
);

SET @ddl := IF(
    @index_exists = 0,
    'ALTER TABLE `sys_audit_log` ADD INDEX `idx_sys_audit_log_tenant_operator_time` (`tenant_id`, `operator`, `occurred_at`, `id`)',
    'SELECT ''idx_sys_audit_log_tenant_operator_time already exists'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;