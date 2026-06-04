CREATE TABLE `wf_process_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `definition_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义键',
  `definition_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义名称',
  `version` int NOT NULL DEFAULT 1 COMMENT '流程版本',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/DEPLOYED/DISABLED',
  `steps_json` json NOT NULL COMMENT '审批步骤定义快照',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_wf_process_definition_tenant_key_version` (`tenant_id` ASC, `definition_key` ASC, `version` ASC) USING BTREE,
  INDEX `idx_wf_process_definition_tenant_status` (`tenant_id` ASC, `status` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流流程定义表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `wf_process_instance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `definition_id` bigint NOT NULL COMMENT '流程定义 ID',
  `definition_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程定义键',
  `definition_version` int NOT NULL COMMENT '流程定义版本',
  `business_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务键',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流程标题',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：RUNNING/APPROVED/REJECTED/WITHDRAWN/TERMINATED',
  `starter_user_id` bigint NOT NULL COMMENT '发起人用户 ID',
  `starter_username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发起人用户名',
  `current_step_index` int NOT NULL DEFAULT 0 COMMENT '当前步骤序号',
  `variables_snapshot_json` json NOT NULL COMMENT '流程变量快照',
  `started_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `ended_at` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_wf_process_instance_tenant_business` (`tenant_id` ASC, `business_key` ASC) USING BTREE,
  INDEX `idx_wf_process_instance_tenant_starter` (`tenant_id` ASC, `starter_user_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_wf_process_instance_tenant_definition` (`tenant_id` ASC, `definition_key` ASC, `definition_version` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流流程实例表' ROW_FORMAT = DYNAMIC;

CREATE TABLE `wf_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `instance_id` bigint NOT NULL COMMENT '流程实例 ID',
  `definition_id` bigint NOT NULL COMMENT '流程定义 ID',
  `step_index` int NOT NULL COMMENT '步骤序号',
  `step_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '步骤名称',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：PENDING/APPROVED/REJECTED/CANCELLED/TRANSFERRED',
  `candidate_user_ids_json` json NULL COMMENT '候选用户 ID 集合',
  `candidate_group_codes_json` json NULL COMMENT '候选组编码集合',
  `assignee_user_id` bigint NULL DEFAULT NULL COMMENT '处理人用户 ID',
  `assignee_username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '处理人用户名',
  `comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '处理意见',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `completed_at` timestamp NULL DEFAULT NULL COMMENT '完成时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_wf_task_tenant_instance` (`tenant_id` ASC, `instance_id` ASC, `step_index` ASC) USING BTREE,
  INDEX `idx_wf_task_tenant_status_assignee` (`tenant_id` ASC, `status` ASC, `assignee_user_id` ASC) USING BTREE,
  INDEX `idx_wf_task_tenant_status_created` (`tenant_id` ASC, `status` ASC, `created_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流任务表' ROW_FORMAT = DYNAMIC;

INSERT INTO `sys_resource` (
  `tenant_id`, `parent_id`, `ancestors`, `resource_type`, `resource_key`, `resource_name`, `route_key`, `grant_key`, `path`, `component`, `icon`, `order_no`, `visible`, `enabled`, `is_system`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`
)
SELECT 'platform', parent.`id`, CONCAT('1,', parent.`id`), 'DIR', 'workflow', '工作流', NULL, NULL, NULL, NULL, 'Connection', 70, 1, 1, 1, 'system', 'system', 0, NOW(), NOW()
FROM `sys_resource` parent
WHERE parent.`tenant_id` = 'platform' AND parent.`resource_key` = 'platform-management' AND parent.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`), `ancestors` = VALUES(`ancestors`), `resource_type` = VALUES(`resource_type`), `resource_name` = VALUES(`resource_name`),
  `route_key` = VALUES(`route_key`), `grant_key` = VALUES(`grant_key`), `path` = VALUES(`path`), `component` = VALUES(`component`), `icon` = VALUES(`icon`),
  `order_no` = VALUES(`order_no`), `visible` = VALUES(`visible`), `enabled` = VALUES(`enabled`), `is_system` = VALUES(`is_system`), `updated_by` = VALUES(`updated_by`), `deleted` = VALUES(`deleted`), `updated_at` = NOW();

INSERT INTO `sys_resource` (
  `tenant_id`, `parent_id`, `ancestors`, `resource_type`, `resource_key`, `resource_name`, `route_key`, `grant_key`, `path`, `component`, `icon`, `order_no`, `visible`, `enabled`, `is_system`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`
)
SELECT 'platform', parent.`id`, CONCAT('1,', parent.`id`), 'MENU', seed.`resource_key`, seed.`resource_name`, seed.`route_key`, seed.`grant_key`, seed.`path`, seed.`component`, seed.`icon`, seed.`order_no`, 1, 1, 1, 'system', 'system', 0, NOW(), NOW()
FROM `sys_resource` parent
JOIN (
  SELECT 'workflow-definitions' AS resource_key, '流程定义' AS resource_name, 'workflow-definitions' AS route_key, 'workflow:read' AS grant_key, '/platform/workflow/definitions' AS path, 'WorkflowDefinitionsView' AS component, 'Tickets' AS icon, 10 AS order_no
  UNION ALL SELECT 'workflow-my-instances', '我的发起', 'workflow-my-instances', NULL, '/platform/workflow/my-instances', 'WorkflowMyInstancesView', 'Document', 20
  UNION ALL SELECT 'workflow-todo', '我的待办', 'workflow-todo', NULL, '/platform/workflow/todo', 'WorkflowTodoView', 'Tickets', 30
  UNION ALL SELECT 'workflow-done', '我的已办', 'workflow-done', NULL, '/platform/workflow/done', 'WorkflowDoneView', 'Document', 40
) seed
WHERE parent.`tenant_id` = 'platform' AND parent.`resource_key` = 'workflow' AND parent.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`), `ancestors` = VALUES(`ancestors`), `resource_type` = VALUES(`resource_type`), `resource_name` = VALUES(`resource_name`),
  `route_key` = VALUES(`route_key`), `grant_key` = VALUES(`grant_key`), `path` = VALUES(`path`), `component` = VALUES(`component`), `icon` = VALUES(`icon`),
  `order_no` = VALUES(`order_no`), `visible` = VALUES(`visible`), `enabled` = VALUES(`enabled`), `is_system` = VALUES(`is_system`), `updated_by` = VALUES(`updated_by`), `deleted` = VALUES(`deleted`), `updated_at` = NOW();

INSERT INTO `sys_resource` (
  `tenant_id`, `parent_id`, `ancestors`, `resource_type`, `resource_key`, `resource_name`, `route_key`, `grant_key`, `path`, `component`, `icon`, `order_no`, `visible`, `enabled`, `is_system`, `created_by`, `updated_by`, `deleted`, `created_at`, `updated_at`
)
SELECT 'platform', parent.`id`, CONCAT('1,', parent.`id`), 'API', seed.`resource_key`, seed.`resource_name`, NULL, seed.`grant_key`, NULL, NULL, NULL, seed.`order_no`, 0, 1, 1, 'system', 'system', 0, NOW(), NOW()
FROM `sys_resource` parent
JOIN (
  SELECT 'api.workflow.read' AS resource_key, '工作流读' AS resource_name, 'workflow:read' AS grant_key, 210 AS order_no
  UNION ALL SELECT 'api.workflow.write', '工作流写', 'workflow:write', 220
  UNION ALL SELECT 'api.codegen.read', '代码生成读', 'codegen:read', 230
  UNION ALL SELECT 'api.codegen.write', '代码生成写', 'codegen:write', 240
) seed
WHERE parent.`tenant_id` = 'platform' AND parent.`resource_key` = 'api' AND parent.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`), `ancestors` = VALUES(`ancestors`), `resource_type` = VALUES(`resource_type`), `resource_name` = VALUES(`resource_name`),
  `grant_key` = VALUES(`grant_key`), `order_no` = VALUES(`order_no`), `visible` = VALUES(`visible`), `enabled` = VALUES(`enabled`), `is_system` = VALUES(`is_system`),
  `updated_by` = VALUES(`updated_by`), `deleted` = VALUES(`deleted`), `updated_at` = NOW();

INSERT INTO `sys_role_resource` (`tenant_id`, `role_id`, `resource_id`, `created_by`, `updated_by`, `created_at`, `updated_at`)
SELECT 'platform', role.`id`, resource.`id`, 'system', 'system', NOW(), NOW()
FROM `sys_role` role
JOIN `sys_resource` resource
  ON resource.`tenant_id` = 'platform'
  AND resource.`resource_key` IN ('workflow', 'workflow-definitions', 'workflow-my-instances', 'workflow-todo', 'workflow-done', 'api.workflow.read', 'api.workflow.write', 'api.codegen.read', 'api.codegen.write')
  AND resource.`deleted` = 0
WHERE role.`tenant_id` = 'platform' AND role.`role_code` = 'ADMIN' AND role.`deleted` = 0
ON DUPLICATE KEY UPDATE `updated_by` = VALUES(`updated_by`), `updated_at` = NOW();