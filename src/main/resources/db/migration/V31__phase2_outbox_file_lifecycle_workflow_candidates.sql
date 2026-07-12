-- Phase 2: outbox、文件生命周期、工作流候选关系（expand-contract 双写）

-- 1) 可靠投递 outbox
CREATE TABLE IF NOT EXISTS `sys_outbox_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户',
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型',
  `aggregate_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '聚合类型',
  `aggregate_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '聚合 ID',
  `payload_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '载荷 JSON',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE/DEAD',
  `attempts` int NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  `max_attempts` int NOT NULL DEFAULT 8 COMMENT '最大尝试次数',
  `next_retry_at` datetime(3) NULL DEFAULT NULL COMMENT '下次重试时间 UTC',
  `last_error` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '最近错误',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间 UTC',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_outbox_poll` (`status` ASC, `next_retry_at` ASC, `id` ASC) USING BTREE,
  INDEX `idx_outbox_tenant_type` (`tenant_id` ASC, `event_type` ASC, `created_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '事务 Outbox 事件表' ROW_FORMAT = DYNAMIC;

-- 2) 文件生命周期状态
ALTER TABLE `sys_storage_file`
  ADD COLUMN `lifecycle_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY'
    COMMENT 'READY/PENDING/FAILED/DELETE_PENDING' AFTER `owner_user_id`;

UPDATE `sys_storage_file` SET `lifecycle_status` = 'READY' WHERE `lifecycle_status` IS NULL OR `lifecycle_status` = '';

ALTER TABLE `sys_storage_file`
  ADD INDEX `idx_sys_storage_file_lifecycle` (`lifecycle_status` ASC, `deleted` ASC, `updated_at` ASC);

-- 3) 工作流候选关系表 + 已办复合索引
CREATE TABLE IF NOT EXISTS `wf_task_candidate_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户',
  `task_id` bigint NOT NULL COMMENT '任务 ID',
  `user_id` bigint NOT NULL COMMENT '候选用户 ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_wf_task_candidate_user` (`tenant_id` ASC, `user_id` ASC, `task_id` ASC) USING BTREE,
  INDEX `idx_wf_task_candidate_user_task` (`tenant_id` ASC, `task_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流任务候选用户' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `wf_task_candidate_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户',
  `task_id` bigint NOT NULL COMMENT '任务 ID',
  `role_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '候选角色编码',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间 UTC',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_wf_task_candidate_role` (`tenant_id` ASC, `role_code` ASC, `task_id` ASC) USING BTREE,
  INDEX `idx_wf_task_candidate_role_task` (`tenant_id` ASC, `task_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流任务候选角色' ROW_FORMAT = DYNAMIC;

-- 已办查询复合索引（tenant + assignee + deleted + completed_at）
ALTER TABLE `wf_task`
  ADD INDEX `idx_wf_task_done` (`tenant_id` ASC, `assignee_user_id` ASC, `deleted` ASC, `completed_at` DESC, `id` DESC);

-- 回填候选关系（从 JSON 展开；JSON_TABLE 需 MySQL 8）
INSERT IGNORE INTO `wf_task_candidate_user` (`tenant_id`, `task_id`, `user_id`)
SELECT t.`tenant_id`, t.`id`, jt.`user_id`
FROM `wf_task` t
JOIN JSON_TABLE(
  IFNULL(t.`candidate_user_ids_json`, '[]'),
  '$[*]' COLUMNS (`user_id` bigint PATH '$')
) jt
WHERE t.`deleted` = 0
  AND t.`candidate_user_ids_json` IS NOT NULL
  AND JSON_LENGTH(t.`candidate_user_ids_json`) > 0;

INSERT IGNORE INTO `wf_task_candidate_role` (`tenant_id`, `task_id`, `role_code`)
SELECT t.`tenant_id`, t.`id`, jt.`role_code`
FROM `wf_task` t
JOIN JSON_TABLE(
  IFNULL(t.`candidate_group_codes_json`, '[]'),
  '$[*]' COLUMNS (`role_code` varchar(64) PATH '$')
) jt
WHERE t.`deleted` = 0
  AND t.`candidate_group_codes_json` IS NOT NULL
  AND JSON_LENGTH(t.`candidate_group_codes_json`) > 0;