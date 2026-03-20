SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `sys_tenant_change_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户编码',
  `change_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更类型',
  `field_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段键',
  `old_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '旧值',
  `new_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '新值',
  `summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '变更摘要',
  `operator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作人',
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_tenant_change_log_tenant_time`(`tenant_id` ASC, `occurred_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户变更记录表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_audit_export_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户编码',
  `operator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发起人',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务状态',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '导出文件名',
  `record_count` int NOT NULL DEFAULT 0 COMMENT '记录数',
  `query_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '查询条件 JSON',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败原因',
  `file_content` longblob NULL COMMENT '导出文件内容',
  `requested_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `completed_at` timestamp NULL DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_audit_export_task_tenant_time`(`tenant_id` ASC, `requested_at` DESC) USING BTREE,
  INDEX `idx_sys_audit_export_task_operator_status`(`operator` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审计导出任务表' ROW_FORMAT = DYNAMIC;

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`)
SELECT 'platform', 'system.category.dict.oauth', 'OAuth 字典', 'oauth.*,scope.*', 'system', 'system', 0, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_config` WHERE `tenant_id` = 'platform' AND `config_key` = 'system.category.dict.oauth' AND `deleted` = 0
);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`)
SELECT 'platform', 'system.category.dict.user', '用户域字典', 'user.*,dept.*,role.*', 'system', 'system', 0, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_config` WHERE `tenant_id` = 'platform' AND `config_key` = 'system.category.dict.user' AND `deleted` = 0
);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`)
SELECT 'platform', 'system.category.config.auth', '认证参数', 'auth.*,oauth.*', 'system', 'system', 0, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_config` WHERE `tenant_id` = 'platform' AND `config_key` = 'system.category.config.auth' AND `deleted` = 0
);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`)
SELECT 'platform', 'system.category.config.platform', '平台参数', 'tenant.*,system.*,feature.*', 'system', 'system', 0, NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_config` WHERE `tenant_id` = 'platform' AND `config_key` = 'system.category.config.platform' AND `deleted` = 0
);
