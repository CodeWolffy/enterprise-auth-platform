-- Preserve sub-second audit timestamps so exclusive time range queries do not drop freshly written events.
ALTER TABLE `sys_audit_log`
  MODIFY COLUMN `occurred_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '事件发生时间';