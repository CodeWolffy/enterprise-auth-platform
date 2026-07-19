-- Dashboard aggregates and background recovery use stable equality/range combinations.
-- Keep the V31 idx_outbox_poll(status, next_retry_at, id) index: it bounds eligible
-- events before sorting and is preferable to scanning every pending event by id.

ALTER TABLE `sys_log`
  ADD INDEX `idx_sys_log_dashboard_scope`
    (`tenant_id` ASC, `deleted` ASC, `created_at` ASC);

ALTER TABLE `sys_login_log`
  ADD INDEX `idx_sys_login_log_dashboard_status`
    (`tenant_id` ASC, `deleted` ASC, `status` ASC, `created_at` ASC);

ALTER TABLE `sys_tenant`
  ADD INDEX `idx_sys_tenant_package_deleted`
    (`package_code` ASC, `deleted` ASC);

-- Cover both platform-wide and tenant/owner-scoped storage aggregates without
-- reading the substantially wider base table rows.
ALTER TABLE `sys_storage_file`
  ADD INDEX `idx_sys_storage_file_dashboard_stats`
    (`deleted` ASC, `tenant_id` ASC, `owner_user_id` ASC,
     `lifecycle_status` ASC, `file_size` ASC);

ALTER TABLE `sys_outbox_event`
  ADD INDEX `idx_outbox_processing_timeout`
    (`status` ASC, `updated_at` ASC);
