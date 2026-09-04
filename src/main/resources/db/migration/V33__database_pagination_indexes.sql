-- Database-side pagination support for workflow tasks and the unified notification inbox.
-- The leading tenant/user predicates keep the existing tenant isolation while
-- the trailing ordering columns reduce filesort work for common page queries.

ALTER TABLE `wf_task`
  ADD INDEX `idx_wf_task_todo_page`
    (`tenant_id` ASC, `status` ASC, `deleted` ASC, `assignee_user_id` ASC,
     `created_at` ASC, `id` ASC);

ALTER TABLE `wf_task_candidate_user`
  ADD INDEX `idx_wf_task_candidate_user_lookup`
    (`tenant_id` ASC, `task_id` ASC, `user_id` ASC);

ALTER TABLE `wf_task_candidate_role`
  ADD INDEX `idx_wf_task_candidate_role_lookup`
    (`tenant_id` ASC, `task_id` ASC, `role_code` ASC);

ALTER TABLE `sys_user_notification`
  ADD INDEX `idx_sys_user_notification_inbox_page`
    (`tenant_id` ASC, `recipient_user_id` ASC, `deleted` ASC, `read_at` ASC,
     `created_at` DESC, `id` DESC);

ALTER TABLE `sys_notice`
  ADD INDEX `idx_sys_notice_inbox_page`
    (`tenant_id` ASC, `deleted` ASC, `published` ASC, `publish_time` ASC,
     `created_at` DESC, `id` DESC);

ALTER TABLE `sys_notice_read_status`
  ADD INDEX `idx_sys_notice_read_status_inbox`
    (`tenant_id` ASC, `user_id` ASC, `deleted` ASC, `notice_id` ASC,
     `cleared_at` ASC, `read_at` ASC);
