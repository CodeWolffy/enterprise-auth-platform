/*
 Navicat Premium Dump SQL

 Source Server         : 本地数据库
 Source Server Type    : MySQL
 Source Server Version : 80043 (8.0.43)
 Source Host           : localhost:3306
 Source Schema         : enterprise_auth_platform

 Target Server Type    : MySQL
 Target Server Version : 80043 (8.0.43)
 File Encoding         : 65001

 Date: 05/04/2026 23:45:51
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for sys_audit_export_policy
-- ----------------------------
DROP TABLE IF EXISTS `sys_audit_export_policy`;
CREATE TABLE `sys_audit_export_policy`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `retention_days` int NOT NULL DEFAULT 7 COMMENT '保留天数',
  `max_tasks` int NOT NULL DEFAULT 100 COMMENT '最大任务数',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_audit_export_policy_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审计导出保留策略表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_audit_export_policy
-- ----------------------------
INSERT INTO `sys_audit_export_policy` VALUES (1, 'platform', 9, 120, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');

-- ----------------------------
-- Table structure for sys_audit_export_task
-- ----------------------------
DROP TABLE IF EXISTS `sys_audit_export_task`;
CREATE TABLE `sys_audit_export_task`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 153 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审计导出任务表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_audit_export_task
-- ----------------------------

-- ----------------------------
-- Table structure for sys_audit_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_audit_log`;
CREATE TABLE `sys_audit_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审计事件类型',
  `operator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作人用户名或系统主体',
  `payload_json` json NULL COMMENT '审计事件 JSON 载荷',
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件发生时间',
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '请求链路标识',
  `client_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户端 IP',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_audit_log_tenant_time`(`tenant_id` ASC, `occurred_at` ASC) USING BTREE,
  INDEX `idx_sys_audit_log_event`(`event_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1246 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '安全审计日志表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_audit_log
-- ----------------------------
INSERT INTO `sys_audit_log` VALUES (1124, 'platform', 'LOGIN_SUCCESS', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"5458b75d-0fe0-4d6c-b93c-019d4d156212\", \"sessionId\": \"bf9f7b83-4777-4755-857a-7028899870c9\"}', '2026-04-05 12:47:47', '5458b75d-0fe0-4d6c-b93c-019d4d156212', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1125, 'platform', 'LOGIN_SUCCESS', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"1dae2351-4132-4c15-87ab-2c9bc6251135\", \"sessionId\": \"2ab801b2-9ff5-4666-91bc-eedf3dea966f\"}', '2026-04-05 12:48:23', '1dae2351-4132-4c15-87ab-2c9bc6251135', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1126, 'platform', 'ROLE_RESOURCE_ASSIGNED', 'admin', '{\"roleId\": 1, \"clientIp\": \"127.0.0.1\", \"roleCode\": \"ADMIN\", \"requestId\": \"1ac226dc-1216-4bc5-8fce-721950f8e951\", \"resourceIds\": [1, 20, 26, 260, 10, 21, 210, 211, 212, 22, 23, 24, 25, 220, 230, 300, 301, 302, 303, 240, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 250, 314, 315, 316, 317]}', '2026-04-05 12:53:03', '1ac226dc-1216-4bc5-8fce-721950f8e951', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1133, 'platform', 'AUDIT_EXPORT_TASK_ARCHIVED', 'admin', '{\"taskId\": 133, \"clientIp\": \"127.0.0.1\", \"fileName\": \"audit-archive-single-ut.csv\", \"requestId\": \"849b1776-b95b-4347-bcf4-a8d2a414b329\"}', '2026-04-05 14:09:08', '849b1776-b95b-4347-bcf4-a8d2a414b329', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1134, 'platform', 'AUDIT_EXPORT_POLICY_GOVERNED', 'admin', '{\"deleted\": 0, \"scanned\": 1, \"archived\": 0, \"clientIp\": \"127.0.0.1\", \"maxTasks\": 120, \"requestId\": \"849b1776-b95b-4347-bcf4-a8d2a414b329\", \"retentionDays\": 9}', '2026-04-05 14:09:08', '849b1776-b95b-4347-bcf4-a8d2a414b329', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1135, 'platform', 'AUDIT_EXPORT_TASK_BATCH_ARCHIVED', 'admin', '{\"status\": \"SUCCESS\", \"affected\": 1, \"clientIp\": \"127.0.0.1\", \"tenantId\": \"platform\", \"requestId\": \"993207aa-431d-454d-bc56-9cd78074ffa1\", \"completedBeforeEpochMs\": 1775311748108}', '2026-04-05 14:09:08', '993207aa-431d-454d-bc56-9cd78074ffa1', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1138, 'platform', 'AUDIT_EXPORT_TASK_CREATED', 'admin', '{\"taskId\": 135, \"clientIp\": \"127.0.0.1\", \"fileName\": \"audit-export-1775398148322.xlsx\", \"requestId\": \"f1a9a888-ae0d-4ce6-a2ac-1693173ae937\"}', '2026-04-05 14:09:08', 'f1a9a888-ae0d-4ce6-a2ac-1693173ae937', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1141, 'platform', 'AUDIT_EXPORT_POLICY_UPDATED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"maxTasks\": 120, \"requestId\": \"2e5390c5-84d3-4e40-9d30-bab883c9abcc\", \"retentionDays\": 9}', '2026-04-05 14:09:10', '2e5390c5-84d3-4e40-9d30-bab883c9abcc', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1146, 'platform', 'AUDIT_EXPORT_TASK_RETRIED', 'admin', '{\"status\": \"FAILED\", \"clientIp\": \"127.0.0.1\", \"requestId\": \"5dfb5b2c-0c49-481f-afe7-d194f7a0d68a\", \"sourceTaskId\": 138}', '2026-04-05 14:09:10', '5dfb5b2c-0c49-481f-afe7-d194f7a0d68a', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1147, 'platform', 'AUDIT_EXPORT_TASK_CREATED', 'admin', '{\"taskId\": 139, \"clientIp\": \"127.0.0.1\", \"fileName\": \"audit-export-1775398150026.xlsx\", \"requestId\": \"5dfb5b2c-0c49-481f-afe7-d194f7a0d68a\"}', '2026-04-05 14:09:10', '5dfb5b2c-0c49-481f-afe7-d194f7a0d68a', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1150, 'platform', 'AUDIT_EXPORTED', 'admin', '{\"clientIp\": \"10.10.10.10\", \"operator\": \"\", \"eventType\": \"AUDIT_EXPORT_UT\", \"requestId\": \"\", \"toEpochMs\": 1775401750115, \"fromEpochMs\": 1775394550115, \"recordCount\": 0}', '2026-04-05 14:09:10', '', '10.10.10.10');
INSERT INTO `sys_audit_log` VALUES (1151, 'platform', 'AUDIT_EXPORT_TASK_COMPLETED', 'admin', '{\"taskId\": 135, \"recordCount\": 0}', '2026-04-05 14:09:12', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1154, 'platform', 'AUDIT_EXPORT_POLICY_GOVERNED', 'admin', '{\"deleted\": 1, \"scanned\": 1, \"archived\": 1, \"clientIp\": \"127.0.0.1\", \"maxTasks\": 120, \"requestId\": \"93c6201b-8678-4964-aeb6-13e688943fd0\", \"retentionDays\": 9}', '2026-04-05 14:09:12', '93c6201b-8678-4964-aeb6-13e688943fd0', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1157, 'platform', 'AUDIT_EXPORT_TASK_DELETED', 'admin', '{\"status\": \"SUCCESS\", \"taskId\": 141, \"clientIp\": \"127.0.0.1\", \"requestId\": \"1c3c3d65-721d-4dc5-bcfd-5fdfe93832e5\"}', '2026-04-05 14:09:12', '1c3c3d65-721d-4dc5-bcfd-5fdfe93832e5', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1158, 'platform', 'AUDIT_EXPORT_TASK_CLEANED', 'admin', '{\"status\": \"FAILED\", \"affected\": 1, \"clientIp\": \"127.0.0.1\", \"tenantId\": \"platform\", \"requestId\": \"9d6c2515-e763-4400-b0e8-82fb964223f4\", \"completedBeforeEpochMs\": 1775311752148}', '2026-04-05 14:09:12', '9d6c2515-e763-4400-b0e8-82fb964223f4', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1159, 'platform', 'USER_CREATED', 'alice', '{\"bizId\": \"u-1\"}', '2026-04-05 14:09:17', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1160, 'platform', 'USER_UPDATED', 'alice', '{\"bizId\": \"u-2\"}', '2026-04-05 14:09:19', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1161, 'platform', 'USER_UPDATED', 'bob', '{\"bizId\": \"u-3\"}', '2026-04-05 14:09:19', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1162, 'tenant-a', 'AUDIT_SCOPE_TEST', 'audit_visible_user_ut', '{\"bizId\": \"visible\"}', '2026-04-05 14:09:20', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1163, 'tenant-a', 'AUDIT_SCOPE_TEST', 'audit_hidden_user_ut', '{\"bizId\": \"hidden\"}', '2026-04-05 14:09:20', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1164, 'tenant-a', 'USER_CREATED', 'system', '{\"userId\": 1470, \"clientIp\": \"127.0.0.1\", \"username\": \"register_api_ut_7672d7bd848b\", \"requestId\": \"7e8c1dd8-91ca-4a6c-8d57-afcba3d339f1\"}', '2026-04-05 14:09:24', '7e8c1dd8-91ca-4a6c-8d57-afcba3d339f1', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1165, 'tenant-a', 'USER_CREATED', 'system', '{\"userId\": 1471, \"clientIp\": \"127.0.0.1\", \"username\": \"register_api_ut_b13deaa722f8\", \"requestId\": \"361732a4-e627-457e-b31d-31d35d0db7e1\"}', '2026-04-05 14:09:25', '361732a4-e627-457e-b31d-31d35d0db7e1', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1166, 'tenant-a', 'REGISTER_RATE_LIMITED', 'register_api_ut_1962ee2f3aa1', '{\"clientIp\": \"127.0.0.1\", \"username\": \"register_api_ut_1962ee2f3aa1\", \"requestId\": \"39bb18c3-4cf0-45bc-94d3-3a1ff3af749a\", \"ipAttempts\": 6, \"windowSeconds\": 600, \"userIpAttempts\": 3}', '2026-04-05 14:09:25', '39bb18c3-4cf0-45bc-94d3-3a1ff3af749a', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1167, 'platform', 'LOGIN_SUCCESS', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"5b2a0fd8-a54f-4a8e-bed3-7429f5c6245c\", \"sessionId\": \"e8e5d58a-e951-416b-ba72-605788fb9fdf\"}', '2026-04-05 14:09:33', '5b2a0fd8-a54f-4a8e-bed3-7429f5c6245c', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1168, 'platform', 'LOGOUT', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"ef8d9169-950d-4b95-9420-f6035c26bbac\", \"sessionId\": \"e8e5d58a-e951-416b-ba72-605788fb9fdf\"}', '2026-04-05 14:09:33', 'ef8d9169-950d-4b95-9420-f6035c26bbac', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1169, 'platform', 'LOGIN_SUCCESS', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"5a176d9a-f956-4342-b04b-a564f5118ea4\", \"sessionId\": \"9022c4d8-b62f-4276-86fe-51b29a969291\"}', '2026-04-05 14:09:34', '5a176d9a-f956-4342-b04b-a564f5118ea4', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1170, 'tenant-a', 'LOGIN_SUCCESS', 'session_flow_tenant_user', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"2d2a8181-4b4f-4dca-90bd-d98177748456\", \"sessionId\": \"67bc36cd-3dfc-4658-b063-baf0a9f08fe6\"}', '2026-04-05 14:09:34', '2d2a8181-4b4f-4dca-90bd-d98177748456', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1171, 'platform', 'DEPT_CREATED', 'system', '{\"deptId\": 380}', '2026-04-05 14:09:38', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1172, 'platform', 'DEPT_DELETED', 'system', '{\"deptId\": 380}', '2026-04-05 14:09:38', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1173, 'tenant-a', 'SYSTEM_CATEGORY_CREATED', 'system_scope_user_ut', '{\"code\": \"system-test-ut\", \"clientIp\": \"127.0.0.1\", \"requestId\": \"4b7b8712-c2c3-4723-96d9-49faffa7a567\", \"targetType\": \"dict\"}', '2026-04-05 14:09:42', '4b7b8712-c2c3-4723-96d9-49faffa7a567', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1174, 'platform', 'DICT_CREATED', 'system', '{\"dictId\": 368}', '2026-04-05 14:09:43', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1175, 'platform', 'DICT_DELETED', 'system', '{\"dictId\": 368}', '2026-04-05 14:09:43', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1176, 'platform', 'TENANT_CAPABILITY_CREATED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"0a850fc8-de60-4869-97f6-14545ac6d8b8\", \"capabilityCode\": \"tenant_capability_ut\"}', '2026-04-05 14:09:43', '0a850fc8-de60-4869-97f6-14545ac6d8b8', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1177, 'platform', 'TENANT_PACKAGE_CREATED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"6cdd8008-3c95-49b4-84f0-dbea747acc39\", \"packageCode\": \"tenant_package_ut\"}', '2026-04-05 14:09:43', '6cdd8008-3c95-49b4-84f0-dbea747acc39', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1178, 'platform', 'TENANT_CAPABILITY_UPDATED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"bb62d813-3f6c-477f-95d6-14bc39b8a5e6\", \"capabilityId\": 20, \"capabilityCode\": \"tenant_capability_ut\"}', '2026-04-05 14:09:43', 'bb62d813-3f6c-477f-95d6-14bc39b8a5e6', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1179, 'platform', 'TENANT_PACKAGE_UPDATED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"packageId\": 14, \"requestId\": \"83c65e57-8041-4f9c-9a78-2b56927c216c\", \"packageCode\": \"tenant_package_ut\"}', '2026-04-05 14:09:43', '83c65e57-8041-4f9c-9a78-2b56927c216c', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1180, 'platform', 'TENANT_PACKAGE_DELETED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"packageId\": 14, \"requestId\": \"577bf27e-524e-4f95-b84a-82206a394831\", \"packageCode\": \"tenant_package_ut\"}', '2026-04-05 14:09:43', '577bf27e-524e-4f95-b84a-82206a394831', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1181, 'platform', 'TENANT_CAPABILITY_DELETED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"e88a93fc-addb-4427-be7a-336956607d24\", \"capabilityId\": 20, \"capabilityCode\": \"tenant_capability_ut\"}', '2026-04-05 14:09:43', 'e88a93fc-addb-4427-be7a-336956607d24', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1182, 'tenant-a', 'TENANT_CAPABILITY_OVERRIDES_UPDATED', 'tester', '{\"clientIp\": \"127.0.0.1\", \"tenantId\": \"tenant-a\", \"requestId\": \"0fa807e0-b884-4124-8bb6-bf527e0f075b\"}', '2026-04-05 14:09:44', '0fa807e0-b884-4124-8bb6-bf527e0f075b', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1183, 'tenant-a', 'SECURITY_ACCESS_DENIED', 'user_controller_scope_ut', '{\"path\": \"/api/users\", \"method\": \"GET\", \"origin\": \"null\", \"reason\": \"access_denied\", \"referer\": \"null\", \"clientIp\": \"127.0.0.1\", \"requestId\": \"4181c9cf-8628-4f20-880b-a101c3d47ba4\", \"userAgent\": \"null\"}', '2026-04-05 14:09:46', '4181c9cf-8628-4f20-880b-a101c3d47ba4', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1184, 'tenant-a', 'ROLE_RESOURCE_ASSIGNED', 'tester', '{\"roleId\": 85, \"clientIp\": \"127.0.0.1\", \"roleCode\": \"RESOURCE_AUTH_V2_UT_1775398974537\", \"requestId\": \"59547b0c-359b-4630-aa3c-bebfdbfe07c3\", \"resourceIds\": [1, 20, 25]}', '2026-04-05 14:22:55', '59547b0c-359b-4630-aa3c-bebfdbfe07c3', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1185, 'tenant-a', 'TENANT_RESOURCE_OVERRIDE_UPDATED', 'tester', '{\"count\": 1, \"clientIp\": \"127.0.0.1\", \"tenantId\": \"tenant-a\", \"requestId\": \"b0fec7fe-3ab6-4b19-b629-bfa02f01cde5\"}', '2026-04-05 14:22:55', 'b0fec7fe-3ab6-4b19-b629-bfa02f01cde5', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1192, 'platform', 'AUDIT_EXPORT_TASK_ARCHIVED', 'admin', '{\"taskId\": 143, \"clientIp\": \"127.0.0.1\", \"fileName\": \"audit-archive-single-ut.csv\", \"requestId\": \"8baa8f24-a9e5-485e-aebc-8af6d6debafb\"}', '2026-04-05 14:24:10', '8baa8f24-a9e5-485e-aebc-8af6d6debafb', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1193, 'platform', 'AUDIT_EXPORT_POLICY_GOVERNED', 'admin', '{\"deleted\": 0, \"scanned\": 1, \"archived\": 0, \"clientIp\": \"127.0.0.1\", \"maxTasks\": 120, \"requestId\": \"8baa8f24-a9e5-485e-aebc-8af6d6debafb\", \"retentionDays\": 9}', '2026-04-05 14:24:10', '8baa8f24-a9e5-485e-aebc-8af6d6debafb', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1194, 'platform', 'AUDIT_EXPORT_TASK_BATCH_ARCHIVED', 'admin', '{\"status\": \"SUCCESS\", \"affected\": 1, \"clientIp\": \"127.0.0.1\", \"tenantId\": \"platform\", \"requestId\": \"a42ab5b5-8a37-4c99-93ec-913a3eeb3ee5\", \"completedBeforeEpochMs\": 1775312650091}', '2026-04-05 14:24:10', 'a42ab5b5-8a37-4c99-93ec-913a3eeb3ee5', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1197, 'platform', 'AUDIT_EXPORT_TASK_CREATED', 'admin', '{\"taskId\": 145, \"clientIp\": \"127.0.0.1\", \"fileName\": \"audit-export-1775399050221.xlsx\", \"requestId\": \"28a41cd8-81c1-491f-b8c3-354e0269231d\"}', '2026-04-05 14:24:10', '28a41cd8-81c1-491f-b8c3-354e0269231d', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1200, 'platform', 'AUDIT_EXPORT_POLICY_UPDATED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"maxTasks\": 120, \"requestId\": \"781c6433-2a9e-4ce2-8ba8-d0f9d944c738\", \"retentionDays\": 9}', '2026-04-05 14:24:11', '781c6433-2a9e-4ce2-8ba8-d0f9d944c738', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1205, 'platform', 'AUDIT_EXPORT_TASK_RETRIED', 'admin', '{\"status\": \"FAILED\", \"clientIp\": \"127.0.0.1\", \"requestId\": \"e749d02e-caa0-4132-a2ef-5e05e2f7b48f\", \"sourceTaskId\": 148}', '2026-04-05 14:24:11', 'e749d02e-caa0-4132-a2ef-5e05e2f7b48f', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1206, 'platform', 'AUDIT_EXPORT_TASK_CREATED', 'admin', '{\"taskId\": 149, \"clientIp\": \"127.0.0.1\", \"fileName\": \"audit-export-1775399050997.xlsx\", \"requestId\": \"e749d02e-caa0-4132-a2ef-5e05e2f7b48f\"}', '2026-04-05 14:24:11', 'e749d02e-caa0-4132-a2ef-5e05e2f7b48f', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1209, 'platform', 'AUDIT_EXPORTED', 'admin', '{\"clientIp\": \"10.10.10.10\", \"operator\": \"\", \"eventType\": \"AUDIT_EXPORT_UT\", \"requestId\": \"\", \"toEpochMs\": 1775402651081, \"fromEpochMs\": 1775395451081, \"recordCount\": 0}', '2026-04-05 14:24:11', '', '10.10.10.10');
INSERT INTO `sys_audit_log` VALUES (1210, 'platform', 'AUDIT_EXPORT_TASK_COMPLETED', 'admin', '{\"taskId\": 145, \"recordCount\": 0}', '2026-04-05 14:24:11', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1211, 'platform', 'AUDIT_EXPORT_TASK_COMPLETED', 'admin', '{\"taskId\": 149, \"recordCount\": 0}', '2026-04-05 14:24:11', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1214, 'platform', 'AUDIT_EXPORT_POLICY_GOVERNED', 'admin', '{\"deleted\": 1, \"scanned\": 1, \"archived\": 1, \"clientIp\": \"127.0.0.1\", \"maxTasks\": 120, \"requestId\": \"c50b8db9-8f80-48f0-a03c-4b489f09fcc5\", \"retentionDays\": 9}', '2026-04-05 14:24:12', 'c50b8db9-8f80-48f0-a03c-4b489f09fcc5', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1217, 'platform', 'AUDIT_EXPORT_TASK_DELETED', 'admin', '{\"status\": \"SUCCESS\", \"taskId\": 151, \"clientIp\": \"127.0.0.1\", \"requestId\": \"e6974f9b-4a90-456d-a64a-86d1644bfd85\"}', '2026-04-05 14:24:12', 'e6974f9b-4a90-456d-a64a-86d1644bfd85', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1218, 'platform', 'AUDIT_EXPORT_TASK_CLEANED', 'admin', '{\"status\": \"FAILED\", \"affected\": 1, \"clientIp\": \"127.0.0.1\", \"tenantId\": \"platform\", \"requestId\": \"7a4c50d8-c6ad-49b0-aecb-a92f1c7f7a7a\", \"completedBeforeEpochMs\": 1775312651821}', '2026-04-05 14:24:12', '7a4c50d8-c6ad-49b0-aecb-a92f1c7f7a7a', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1219, 'platform', 'USER_CREATED', 'alice', '{\"bizId\": \"u-1\"}', '2026-04-05 14:24:14', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1220, 'platform', 'USER_UPDATED', 'alice', '{\"bizId\": \"u-2\"}', '2026-04-05 14:24:16', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1221, 'platform', 'USER_UPDATED', 'bob', '{\"bizId\": \"u-3\"}', '2026-04-05 14:24:16', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1222, 'tenant-a', 'AUDIT_SCOPE_TEST', 'audit_visible_user_ut', '{\"bizId\": \"visible\"}', '2026-04-05 14:24:16', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1223, 'tenant-a', 'AUDIT_SCOPE_TEST', 'audit_hidden_user_ut', '{\"bizId\": \"hidden\"}', '2026-04-05 14:24:16', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1224, 'tenant-a', 'USER_CREATED', 'system', '{\"userId\": 1538, \"clientIp\": \"127.0.0.1\", \"username\": \"register_api_ut_49aeef879f15\", \"requestId\": \"df314c7b-404f-4f2b-8642-cf43c0bc6cb5\"}', '2026-04-05 14:24:19', 'df314c7b-404f-4f2b-8642-cf43c0bc6cb5', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1225, 'tenant-a', 'USER_CREATED', 'system', '{\"userId\": 1539, \"clientIp\": \"127.0.0.1\", \"username\": \"register_api_ut_82e5d690ed34\", \"requestId\": \"4fdfc2c6-6e04-4efe-9573-dbd637690949\"}', '2026-04-05 14:24:19', '4fdfc2c6-6e04-4efe-9573-dbd637690949', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1226, 'tenant-a', 'REGISTER_RATE_LIMITED', 'register_api_ut_0e61493f1128', '{\"clientIp\": \"127.0.0.1\", \"username\": \"register_api_ut_0e61493f1128\", \"requestId\": \"985ba997-9799-40e5-b95a-18ba5224d352\", \"ipAttempts\": 6, \"windowSeconds\": 600, \"userIpAttempts\": 3}', '2026-04-05 14:24:20', '985ba997-9799-40e5-b95a-18ba5224d352', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1227, 'platform', 'LOGIN_SUCCESS', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"accbf7c0-52ac-4b32-bc1b-7b054c42da5f\", \"sessionId\": \"294d808e-42ac-4cde-a8b5-543a4f1dca3b\"}', '2026-04-05 14:24:25', 'accbf7c0-52ac-4b32-bc1b-7b054c42da5f', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1228, 'platform', 'LOGOUT', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"182bedc4-4a09-4d1f-b4b6-062633b60306\", \"sessionId\": \"294d808e-42ac-4cde-a8b5-543a4f1dca3b\"}', '2026-04-05 14:24:25', '182bedc4-4a09-4d1f-b4b6-062633b60306', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1229, 'platform', 'LOGIN_SUCCESS', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"eacf4fca-994c-45fc-a1fd-9145bb4473ae\", \"sessionId\": \"69bcd3c9-6832-43a3-a62a-8b8806e8084d\"}', '2026-04-05 14:24:25', 'eacf4fca-994c-45fc-a1fd-9145bb4473ae', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1230, 'tenant-a', 'LOGIN_SUCCESS', 'session_flow_tenant_user', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"5f187dd9-a4df-4d80-8a18-da825403d4ad\", \"sessionId\": \"9e6f29d9-12df-44ab-876d-84a2b8963475\"}', '2026-04-05 14:24:26', '5f187dd9-a4df-4d80-8a18-da825403d4ad', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1231, 'platform', 'DEPT_CREATED', 'system', '{\"deptId\": 397}', '2026-04-05 14:24:27', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1232, 'platform', 'DEPT_DELETED', 'system', '{\"deptId\": 397}', '2026-04-05 14:24:27', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1233, 'tenant-a', 'ROLE_RESOURCE_ASSIGNED', 'tester', '{\"roleId\": 93, \"clientIp\": \"127.0.0.1\", \"roleCode\": \"RESOURCE_AUTH_V2_UT_1775399067964\", \"requestId\": \"22368f87-0789-4e9a-b8fa-9a54b3cdd2fe\", \"resourceIds\": [1, 20, 25]}', '2026-04-05 14:24:28', '22368f87-0789-4e9a-b8fa-9a54b3cdd2fe', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1234, 'tenant-a', 'TENANT_RESOURCE_OVERRIDE_UPDATED', 'tester', '{\"count\": 1, \"clientIp\": \"127.0.0.1\", \"tenantId\": \"tenant-a\", \"requestId\": \"358adb00-8001-45f1-9544-f3cf3b13f6a5\"}', '2026-04-05 14:24:28', '358adb00-8001-45f1-9544-f3cf3b13f6a5', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1235, 'tenant-a', 'SYSTEM_CATEGORY_CREATED', 'system_scope_user_ut', '{\"code\": \"system-test-ut\", \"clientIp\": \"127.0.0.1\", \"requestId\": \"2d018b17-c147-4f5a-a067-d4ab0ad5984f\", \"targetType\": \"dict\"}', '2026-04-05 14:24:30', '2d018b17-c147-4f5a-a067-d4ab0ad5984f', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1236, 'platform', 'DICT_CREATED', 'system', '{\"dictId\": 389}', '2026-04-05 14:24:31', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1237, 'platform', 'DICT_DELETED', 'system', '{\"dictId\": 389}', '2026-04-05 14:24:31', NULL, NULL);
INSERT INTO `sys_audit_log` VALUES (1238, 'platform', 'TENANT_CAPABILITY_CREATED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"57d0c92f-6fbd-4c1f-b7af-696a38dad2df\", \"capabilityCode\": \"tenant_capability_ut\"}', '2026-04-05 14:24:31', '57d0c92f-6fbd-4c1f-b7af-696a38dad2df', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1239, 'platform', 'TENANT_PACKAGE_CREATED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"afca9140-127e-4122-beca-7b06012783be\", \"packageCode\": \"tenant_package_ut\"}', '2026-04-05 14:24:31', 'afca9140-127e-4122-beca-7b06012783be', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1240, 'platform', 'TENANT_CAPABILITY_UPDATED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"e8a9b9c1-4bb0-417d-911f-9137f0140e2f\", \"capabilityId\": 21, \"capabilityCode\": \"tenant_capability_ut\"}', '2026-04-05 14:24:31', 'e8a9b9c1-4bb0-417d-911f-9137f0140e2f', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1241, 'platform', 'TENANT_PACKAGE_UPDATED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"packageId\": 15, \"requestId\": \"2629f8d5-8f17-4759-b1fb-9b41355e02af\", \"packageCode\": \"tenant_package_ut\"}', '2026-04-05 14:24:31', '2629f8d5-8f17-4759-b1fb-9b41355e02af', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1242, 'platform', 'TENANT_PACKAGE_DELETED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"packageId\": 15, \"requestId\": \"991d1910-6ace-45d9-aca2-beb0de035cd5\", \"packageCode\": \"tenant_package_ut\"}', '2026-04-05 14:24:31', '991d1910-6ace-45d9-aca2-beb0de035cd5', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1243, 'platform', 'TENANT_CAPABILITY_DELETED', 'admin', '{\"clientIp\": \"127.0.0.1\", \"requestId\": \"e47ee8a3-9f27-484d-86bc-03d9644fdcef\", \"capabilityId\": 21, \"capabilityCode\": \"tenant_capability_ut\"}', '2026-04-05 14:24:31', 'e47ee8a3-9f27-484d-86bc-03d9644fdcef', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1244, 'tenant-a', 'TENANT_CAPABILITY_OVERRIDES_UPDATED', 'tester', '{\"clientIp\": \"127.0.0.1\", \"tenantId\": \"tenant-a\", \"requestId\": \"44ce9782-aa84-4a96-b241-26d27ca04c02\"}', '2026-04-05 14:24:31', '44ce9782-aa84-4a96-b241-26d27ca04c02', '127.0.0.1');
INSERT INTO `sys_audit_log` VALUES (1245, 'tenant-a', 'SECURITY_ACCESS_DENIED', 'user_controller_scope_ut', '{\"path\": \"/api/users\", \"method\": \"GET\", \"origin\": \"null\", \"reason\": \"access_denied\", \"referer\": \"null\", \"clientIp\": \"127.0.0.1\", \"requestId\": \"351d828e-d71c-4b4b-b873-5ec3b2cc7a53\", \"userAgent\": \"null\"}', '2026-04-05 14:24:33', '351d828e-d71c-4b4b-b873-5ec3b2cc7a53', '127.0.0.1');

-- ----------------------------
-- Table structure for sys_category_rule
-- ----------------------------
DROP TABLE IF EXISTS `sys_category_rule`;
CREATE TABLE `sys_category_rule`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `target_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标类型 dict/config',
  `category_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类编码',
  `category_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `matchers` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '匹配规则集合',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_category_rule_tenant_target_code`(`tenant_id` ASC, `target_type` ASC, `category_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统分类规则表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_category_rule
-- ----------------------------
INSERT INTO `sys_category_rule` VALUES (2, 'platform', 'dict', 'user', '用户域字典', 'user.*,dept.*,role.*', 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_category_rule` VALUES (3, 'platform', 'config', 'auth', '认证参数', 'auth.*', 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_category_rule` VALUES (4, 'platform', 'config', 'platform', '平台参数', 'tenant.*,system.*,feature.*', 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `config_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置值',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `config_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置名称',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_config_tenant_key`(`tenant_id` ASC, `config_key` ASC) USING BTREE,
  INDEX `idx_sys_config_tenant_key_lookup`(`tenant_id` ASC, `config_key` ASC) USING BTREE,
  INDEX `idx_sys_config_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_sys_config_tenant_deleted_created_at`(`tenant_id` ASC, `deleted` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_sys_config_tenant_deleted_created_by`(`tenant_id` ASC, `deleted` ASC, `created_by` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 298 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (1, 'platform', 'login.captcha.enabled', 'true', '2026-03-20 08:09:53', NULL, '2026-03-20 08:09:54', NULL, NULL, 0);
INSERT INTO `sys_config` VALUES (2, 'platform', 'registration.default_tenant_id', 'tenant-a', '2026-03-30 18:00:00', '默认注册租户', '2026-04-05 22:24:20', 'system', 'test', 0);
INSERT INTO `sys_config` VALUES (3, 'platform', 'system.category.dict.user', 'user.*,dept.*,role.*', '2026-03-21 04:12:50', '用户域字典', '2026-03-21 04:12:50', 'system', 'system', 0);
INSERT INTO `sys_config` VALUES (4, 'platform', 'system.category.config.auth', 'auth.*', '2026-03-21 04:12:50', '认证参数', '2026-03-21 04:12:50', 'system', 'system', 0);
INSERT INTO `sys_config` VALUES (5, 'platform', 'system.category.config.platform', 'tenant.*,system.*,feature.*', '2026-03-21 04:12:50', '平台参数', '2026-03-21 04:12:50', 'system', 'system', 0);
INSERT INTO `sys_config` VALUES (6, 'platform', 'registration.default_role_codes', 'AUDITOR', '2026-03-30 18:00:00', '默认注册角色', '2026-04-05 22:24:20', 'system', 'test', 0);

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '父部门 ID，根节点为空',
  `dept_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `dept_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门编码',
  `leader_user_id` bigint NULL DEFAULT NULL COMMENT '部门负责人用户 ID',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_dept_tenant_parent`(`tenant_id` ASC, `parent_id` ASC) USING BTREE,
  INDEX `idx_sys_dept_tenant_code`(`tenant_id` ASC, `dept_code` ASC) USING BTREE,
  INDEX `idx_sys_dept_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 412 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '部门树表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO `sys_dept` VALUES (1, 'platform', NULL, '平台运营中心', '2026-03-20 08:09:53', NULL, NULL, '2026-03-20 08:09:53', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (2, 'tenant-a', NULL, '租户A-财务部', '2026-03-20 08:09:53', NULL, NULL, '2026-03-20 08:09:53', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (3, 'tenant-a', NULL, '租户A-研发部', '2026-03-20 08:09:53', NULL, NULL, '2026-03-20 08:09:53', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (4, 'platform', NULL, '平台运维中心', '2026-03-20 08:34:56', NULL, NULL, '2026-03-20 08:34:56', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (5, 'tenant-a', NULL, '租户A-财务', '2026-03-20 08:34:56', NULL, NULL, '2026-03-20 08:34:56', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (6, 'tenant-a', NULL, '租户A-研发', '2026-03-20 08:34:56', NULL, NULL, '2026-03-20 08:34:56', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (380, 'platform', NULL, '测试部门', '2026-04-05 14:09:38', 'TEST_DEPT_45787274453400', NULL, '2026-04-05 14:09:38', 'system', 'system', 1);
INSERT INTO `sys_dept` VALUES (397, 'platform', NULL, '测试部门', '2026-04-05 14:24:27', 'TEST_DEPT_46676202948300', NULL, '2026-04-05 14:24:27', 'system', 'system', 1);

-- ----------------------------
-- Table structure for sys_dict
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `dict_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型编码',
  `dict_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典项编码',
  `dict_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典项值',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_dict_tenant_type_code`(`tenant_id` ASC, `dict_type` ASC, `dict_code` ASC) USING BTREE,
  INDEX `idx_sys_dict_tenant_type`(`tenant_id` ASC, `dict_type` ASC) USING BTREE,
  INDEX `idx_sys_dict_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_sys_dict_tenant_deleted_created_at`(`tenant_id` ASC, `deleted` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_sys_dict_tenant_deleted_created_by`(`tenant_id` ASC, `deleted` ASC, `created_by` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 390 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_dict
-- ----------------------------
INSERT INTO `sys_dict` VALUES (1, 'platform', 'user_status', 'ENABLED', '启用', '2026-03-20 08:09:53', '2026-03-20 08:09:54', NULL, NULL, 0);
INSERT INTO `sys_dict` VALUES (368, 'platform', 'demo', 'k45791575359700', 'v', '2026-04-05 14:09:43', '2026-04-05 14:09:43', 'system', 'system', 1);
INSERT INTO `sys_dict` VALUES (389, 'platform', 'demo', 'k46679539513100', 'v', '2026-04-05 14:24:31', '2026-04-05 14:24:31', 'system', 'system', 1);

-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `notice_title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公告标题',
  `notice_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公告内容',
  `published` tinyint NOT NULL DEFAULT 0 COMMENT '是否发布：1 已发布，0 未发布',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `publish_time` datetime NULL DEFAULT NULL COMMENT '发布时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_notice_tenant_published`(`tenant_id` ASC, `published` ASC) USING BTREE,
  INDEX `idx_sys_notice_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_sys_notice_tenant_deleted_publish_time`(`tenant_id` ASC, `deleted` ASC, `publish_time` ASC) USING BTREE,
  INDEX `idx_sys_notice_tenant_deleted_created_at`(`tenant_id` ASC, `deleted` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_sys_notice_tenant_deleted_created_by`(`tenant_id` ASC, `deleted` ASC, `created_by` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通知公告表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_notice
-- ----------------------------
INSERT INTO `sys_notice` VALUES (1, 'platform', '首期骨架已启用', '系统管理模块的公告、字典、参数基础能力已初始化。', 1, '2026-03-20 08:09:53', NULL, '2026-03-20 08:09:54', NULL, NULL, 0);

-- ----------------------------
-- Table structure for sys_resource
-- ----------------------------
DROP TABLE IF EXISTS `sys_resource`;
CREATE TABLE `sys_resource`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板所属租户，v2 固定为 platform',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '父资源 ID',
  `ancestors` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '祖先节点 ID 列表，逗号分隔',
  `resource_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源类型：DIR-目录/MENU-菜单/BUTTON-按钮/API-接口',
  `resource_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源唯一标识',
  `resource_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源显示名称',
  `route_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前端路由标识',
  `grant_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '统一授权键',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前端路由路径（兼容字段）',
  `component` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前端组件名称（兼容字段）',
  `icon` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '图标标识',
  `order_no` int NOT NULL DEFAULT 0 COMMENT '显示排序',
  `visible` tinyint NOT NULL DEFAULT 1 COMMENT '菜单树中是否可见',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `is_system` tinyint NOT NULL DEFAULT 0 COMMENT '是否系统内置资源',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_resource_tenant_key`(`tenant_id` ASC, `resource_key` ASC) USING BTREE,
  INDEX `idx_sys_resource_tenant_parent`(`tenant_id` ASC, `parent_id` ASC) USING BTREE,
  INDEX `idx_sys_resource_tenant_type`(`tenant_id` ASC, `resource_type` ASC) USING BTREE,
  INDEX `idx_sys_resource_tenant_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_sys_resource_tenant_grant`(`tenant_id` ASC, `grant_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 318 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '统一资源表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_resource
-- ----------------------------
INSERT INTO `sys_resource` VALUES (1, 'platform', NULL, '', 'DIR', 'root', '根节点', NULL, NULL, NULL, NULL, NULL, 0, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (10, 'platform', 1, '1', 'MENU', 'dashboard', '运行总览', 'dashboard', 'auth:read', '/dashboard', 'DashboardView', 'Monitor', 10, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (20, 'platform', 1, '1', 'DIR', 'system', '系统模块', NULL, NULL, NULL, NULL, 'Setting', 20, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (21, 'platform', 20, '1,20', 'MENU', 'users', '用户管理', 'users', 'user:read', '/system/users', 'UsersView', 'Avatar', 10, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (22, 'platform', 20, '1,20', 'MENU', 'roles', '角色管理', 'roles', 'role:read', '/system/roles', 'RolesView', 'Connection', 20, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (23, 'platform', 20, '1,20', 'MENU', 'depts', '部门管理', 'depts', 'dept:read', '/system/depts', 'DepartmentsView', 'OfficeBuilding', 30, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (24, 'platform', 20, '1,20', 'MENU', 'tenants', '租户管理', 'tenants', 'tenant:read', '/system/tenants', 'TenantsView', 'Flag', 40, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (25, 'platform', 20, '1,20', 'MENU', 'audit', '安全审计', 'audit', 'audit:read', '/system/audit', 'AuditView', 'Histogram', 50, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (26, 'platform', 20, '1,20', 'MENU', 'settings', '系统管理', 'settings', 'system:read', '/system/settings', 'SystemManagementView', 'Setting', 60, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (210, 'platform', 21, '1,20,21', 'BUTTON', 'users.create', '新增用户', NULL, 'user:write', NULL, NULL, NULL, 10, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (211, 'platform', 21, '1,20,21', 'BUTTON', 'users.update', '编辑用户', NULL, 'user:write', NULL, NULL, NULL, 20, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (212, 'platform', 21, '1,20,21', 'BUTTON', 'users.delete', '删除用户', NULL, 'user:write', NULL, NULL, NULL, 30, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (220, 'platform', 22, '1,20,22', 'BUTTON', 'roles.assign', '角色授权', NULL, 'role:write', NULL, NULL, NULL, 10, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (230, 'platform', 23, '1,20,23', 'BUTTON', 'depts.manage', '部门维护', NULL, 'dept:write', NULL, NULL, NULL, 10, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (240, 'platform', 24, '1,20,24', 'BUTTON', 'tenants.manage', '租户维护', NULL, 'tenant:write', NULL, NULL, NULL, 10, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (250, 'platform', 25, '1,20,25', 'BUTTON', 'audit.export', '审计导出', NULL, 'audit:write', NULL, NULL, NULL, 10, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (260, 'platform', 26, '1,20,26', 'BUTTON', 'settings.manage', '系统治理', NULL, 'system:write', NULL, NULL, NULL, 10, 1, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (300, 'platform', 1, '1', 'DIR', 'api', 'API 权限', NULL, NULL, NULL, NULL, NULL, 100, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (301, 'platform', 300, '1,300', 'API', 'api.auth.read', '认证读', NULL, 'auth:read', NULL, NULL, NULL, 10, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (302, 'platform', 300, '1,300', 'API', 'api.auth.write', '认证写', NULL, 'auth:write', NULL, NULL, NULL, 20, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (303, 'platform', 300, '1,300', 'API', 'api.user.read', '用户读', NULL, 'user:read', NULL, NULL, NULL, 30, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (304, 'platform', 300, '1,300', 'API', 'api.user.write', '用户写', NULL, 'user:write', NULL, NULL, NULL, 40, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (305, 'platform', 300, '1,300', 'API', 'api.role.read', '角色读', NULL, 'role:read', NULL, NULL, NULL, 50, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (306, 'platform', 300, '1,300', 'API', 'api.role.write', '角色写', NULL, 'role:write', NULL, NULL, NULL, 60, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (307, 'platform', 300, '1,300', 'API', 'api.dept.read', '部门读', NULL, 'dept:read', NULL, NULL, NULL, 70, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (308, 'platform', 300, '1,300', 'API', 'api.dept.write', '部门写', NULL, 'dept:write', NULL, NULL, NULL, 80, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (309, 'platform', 300, '1,300', 'API', 'api.tenant.read', '租户读', NULL, 'tenant:read', NULL, NULL, NULL, 90, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (310, 'platform', 300, '1,300', 'API', 'api.tenant.write', '租户写', NULL, 'tenant:write', NULL, NULL, NULL, 100, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (311, 'platform', 300, '1,300', 'API', 'api.audit.read', '审计读', NULL, 'audit:read', NULL, NULL, NULL, 110, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (312, 'platform', 300, '1,300', 'API', 'api.audit.write', '审计写', NULL, 'audit:write', NULL, NULL, NULL, 120, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (313, 'platform', 300, '1,300', 'API', 'api.system.read', '系统读', NULL, 'system:read', NULL, NULL, NULL, 130, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (314, 'platform', 300, '1,300', 'API', 'api.system.write', '系统写', NULL, 'system:write', NULL, NULL, NULL, 140, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_resource` VALUES (315, 'platform', 300, '1,300', 'API', 'api.session.write', '会话写', NULL, 'session:write', NULL, NULL, NULL, 150, 0, 1, 1, 'system', 'system', 0, '2026-04-05 20:33:52', '2026-04-05 20:33:52');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `role_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色业务编码',
  `role_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `data_scope_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据权限范围类型',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `role_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '角色描述',
  `data_scope_value_json` json NULL COMMENT '数据范围附加值 JSON',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_role_tenant_code`(`tenant_id` ASC, `role_code` ASC) USING BTREE,
  INDEX `idx_sys_role_tenant_scope`(`tenant_id` ASC, `data_scope_type` ASC) USING BTREE,
  INDEX `idx_sys_role_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 94 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, 'platform', 'ADMIN', '平台管理员', 'ALL', '2026-03-20 08:09:53', NULL, NULL, '2026-03-20 08:09:53', NULL, NULL, 0);
INSERT INTO `sys_role` VALUES (2, 'tenant-a', 'AUDITOR', '审计员', 'DEPT', '2026-03-20 08:09:53', NULL, NULL, '2026-03-20 08:09:53', NULL, NULL, 0);

-- ----------------------------
-- Table structure for sys_role_resource
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_resource`;
CREATE TABLE `sys_role_resource`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `resource_id` bigint NOT NULL COMMENT '资源 ID',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_role_resource_tenant_role_resource`(`tenant_id` ASC, `role_id` ASC, `resource_id` ASC) USING BTREE,
  INDEX `idx_sys_role_resource_tenant_role`(`tenant_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_sys_role_resource_tenant_resource`(`tenant_id` ASC, `resource_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 142 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色资源授权表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role_resource
-- ----------------------------
INSERT INTO `sys_role_resource` VALUES (2, 'tenant-a', 2, 21, 'system', 'system', '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_role_resource` VALUES (7, 'tenant-a', 2, 25, 'system', 'system', '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_role_resource` VALUES (20, 'tenant-a', 2, 303, 'system', 'system', '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_role_resource` VALUES (29, 'tenant-a', 2, 311, 'system', 'system', '2026-04-05 20:33:52', '2026-04-05 20:33:52');
INSERT INTO `sys_role_resource` VALUES (82, 'tenant-a', 2, 1, 'system', 'system', '2026-04-05 20:44:11', '2026-04-05 20:44:11');
INSERT INTO `sys_role_resource` VALUES (83, 'tenant-a', 2, 20, 'system', 'system', '2026-04-05 20:44:11', '2026-04-05 20:44:11');
INSERT INTO `sys_role_resource` VALUES (84, 'tenant-a', 2, 300, 'system', 'system', '2026-04-05 20:44:11', '2026-04-05 20:44:11');
INSERT INTO `sys_role_resource` VALUES (86, 'platform', 1, 1, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (87, 'platform', 1, 20, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (88, 'platform', 1, 26, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (89, 'platform', 1, 260, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (90, 'platform', 1, 10, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (91, 'platform', 1, 21, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (92, 'platform', 1, 210, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (93, 'platform', 1, 211, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (94, 'platform', 1, 212, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (95, 'platform', 1, 22, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (96, 'platform', 1, 23, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (97, 'platform', 1, 24, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (98, 'platform', 1, 25, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (99, 'platform', 1, 220, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (100, 'platform', 1, 230, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (101, 'platform', 1, 300, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (102, 'platform', 1, 301, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (103, 'platform', 1, 302, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (104, 'platform', 1, 303, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (105, 'platform', 1, 240, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (106, 'platform', 1, 304, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (107, 'platform', 1, 305, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (108, 'platform', 1, 306, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (109, 'platform', 1, 307, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (110, 'platform', 1, 308, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (111, 'platform', 1, 309, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (112, 'platform', 1, 310, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (113, 'platform', 1, 311, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (114, 'platform', 1, 312, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (115, 'platform', 1, 313, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (116, 'platform', 1, 250, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (117, 'platform', 1, 314, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');
INSERT INTO `sys_role_resource` VALUES (118, 'platform', 1, 315, 'admin', 'admin', '2026-04-05 12:53:03', '2026-04-05 12:53:03');

-- ----------------------------
-- Table structure for sys_tenant
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户业务标识',
  `tenant_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户名称',
  `platform_level` tinyint NOT NULL DEFAULT 0 COMMENT '是否平台级租户：1 是，0 否',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tenant_status` tinyint NOT NULL DEFAULT 1 COMMENT '租户状态：1 启用，0 禁用',
  `expire_at` datetime NULL DEFAULT NULL COMMENT '租户到期时间',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '套餐编码',
  `lifecycle_note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '运营备注',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `tenant_id`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_sys_tenant_status`(`tenant_status` ASC) USING BTREE,
  INDEX `idx_sys_tenant_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户主表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant
-- ----------------------------
INSERT INTO `sys_tenant` VALUES (1, 'platform', '平台租户', 1, '2026-03-20 08:09:53', 1, NULL, 'platform-governance', '负责全局治理与租户运维', '2026-03-21 06:26:06', NULL, NULL, 0);
INSERT INTO `sys_tenant` VALUES (2, 'tenant-a', '租户A', 0, '2026-03-20 08:09:53', 1, NULL, 'business-standard', '默认标准业务租户', '2026-03-21 06:26:06', NULL, NULL, 0);

-- ----------------------------
-- Table structure for sys_tenant_capability
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_capability`;
CREATE TABLE `sys_tenant_capability`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户，当前固定为 platform',
  `capability_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力编码',
  `capability_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力名称',
  `capability_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '能力说明',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_tenant_capability_tenant_code`(`tenant_id` ASC, `capability_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 22 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户能力定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_capability
-- ----------------------------
INSERT INTO `sys_tenant_capability` VALUES (1, 'platform', 'auth', '认证安全', '登录认证、会话治理与安全基线能力', 10, 1, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_capability` VALUES (2, 'platform', 'user', '用户管理', '用户目录、启停、角色分配与组织可见范围管理', 20, 1, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_capability` VALUES (3, 'platform', 'role', '角色授权', '角色模型、权限树与数据范围授权', 30, 1, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_capability` VALUES (4, 'platform', 'dept', '组织管理', '组织树、负责人和部门层级治理', 40, 1, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_capability` VALUES (5, 'platform', 'tenant', '租户治理', '租户套餐、能力配置与生命周期治理', 50, 1, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_capability` VALUES (6, 'platform', 'system', '系统管理', '字典、参数、公告与分类配置管理', 60, 1, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_capability` VALUES (7, 'platform', 'audit', '安全审计', '审计查询、导出与授权记录联动', 70, 1, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_capability` VALUES (8, 'platform', 'notice', '通知公告', '公告发布与租户通知能力', 80, 1, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');

-- ----------------------------
-- Table structure for sys_tenant_capability_override
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_capability_override`;
CREATE TABLE `sys_tenant_capability_override`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户编码',
  `capability_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力编码',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '覆盖后是否启用',
  `capability_desc_override` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '能力说明覆盖',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_tenant_capability_override`(`tenant_id` ASC, `capability_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 27 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户能力覆盖表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_capability_override
-- ----------------------------

-- ----------------------------
-- Table structure for sys_tenant_change_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_change_log`;
CREATE TABLE `sys_tenant_change_log`  (
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
) ENGINE = InnoDB AUTO_INCREMENT = 156 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户变更记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_change_log
-- ----------------------------
INSERT INTO `sys_tenant_change_log` VALUES (131, 'tenant-a', 'CAPABILITY', 'capabilityOverrides', NULL, 'audit:true:审计导出与看板能力|notice:false:null', '更新租户能力覆盖', 'tester', '2026-03-31 05:34:27');
INSERT INTO `sys_tenant_change_log` VALUES (142, 'tenant-a', 'CAPABILITY', 'capabilityOverrides', NULL, 'audit:true:审计导出与看板能力|notice:false:null', '更新租户能力覆盖', 'tester', '2026-04-05 14:09:44');
INSERT INTO `sys_tenant_change_log` VALUES (153, 'tenant-a', 'CAPABILITY', 'capabilityOverrides', NULL, 'audit:true:审计导出与看板能力|notice:false:null', '更新租户能力覆盖', 'tester', '2026-04-05 14:24:31');

-- ----------------------------
-- Table structure for sys_tenant_package
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_package`;
CREATE TABLE `sys_tenant_package`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户，当前固定为 platform',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐编码',
  `package_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐名称',
  `user_quota` int NULL DEFAULT NULL COMMENT '用户配额',
  `storage_quota_gb` int NULL DEFAULT NULL COMMENT '存储配额GB',
  `package_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '套餐说明',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_tenant_package_tenant_code`(`tenant_id` ASC, `package_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户套餐定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_package
-- ----------------------------
INSERT INTO `sys_tenant_package` VALUES (1, 'platform', 'platform-governance', '平台治理版', 9999, 1024, '负责全局治理与租户运维', 1, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_package` VALUES (2, 'platform', 'business-standard', '标准版', 200, 200, '适用于常规业务租户', 1, 'system', 'system', 0, '2026-03-21 06:26:06', '2026-03-21 06:26:06');

-- ----------------------------
-- Table structure for sys_tenant_package_capability
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_package_capability`;
CREATE TABLE `sys_tenant_package_capability`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户，当前固定为 platform',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐编码',
  `capability_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力编码',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_tenant_package_capability`(`tenant_id` ASC, `package_code` ASC, `capability_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 35 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户套餐能力关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_package_capability
-- ----------------------------
INSERT INTO `sys_tenant_package_capability` VALUES (1, 'platform', 'platform-governance', 'auth', 'system', 'system', '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (2, 'platform', 'platform-governance', 'audit', 'system', 'system', '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (3, 'platform', 'platform-governance', 'system', 'system', 'system', '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (4, 'platform', 'platform-governance', 'tenant', 'system', 'system', '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (5, 'platform', 'business-standard', 'user', 'system', 'system', '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (6, 'platform', 'business-standard', 'role', 'system', 'system', '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (7, 'platform', 'business-standard', 'audit', 'system', 'system', '2026-03-21 06:26:06', '2026-03-21 06:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (8, 'platform', 'business-standard', 'notice', 'system', 'system', '2026-03-21 06:26:06', '2026-03-21 06:26:06');

-- ----------------------------
-- Table structure for sys_tenant_resource_override
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_resource_override`;
CREATE TABLE `sys_tenant_resource_override`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `resource_id` bigint NOT NULL COMMENT '资源 ID',
  `enabled` tinyint NULL DEFAULT NULL COMMENT '覆盖启用状态',
  `visible` tinyint NULL DEFAULT NULL COMMENT '覆盖可见状态',
  `order_no` int NULL DEFAULT NULL COMMENT '覆盖排序值',
  `title_override` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '覆盖标题',
  `icon_override` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '覆盖图标',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_tenant_resource_override`(`tenant_id` ASC, `resource_id` ASC) USING BTREE,
  INDEX `idx_sys_tenant_resource_override_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户资源覆盖表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_resource_override
-- ----------------------------

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `dept_id` bigint NULL DEFAULT NULL COMMENT '部门 ID',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名',
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希值',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用，0 禁用',
  `session_version` int NOT NULL DEFAULT 1 COMMENT '会话版本号，用于令牌失效控制',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `display_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户显示名称',
  `mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  `last_login_at` datetime NULL DEFAULT NULL COMMENT '最近登录时间',
  `last_login_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '最近登录 IP',
  `password_updated_at` datetime NULL DEFAULT NULL COMMENT '密码最近修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_user_username`(`username` ASC) USING BTREE,
  INDEX `idx_sys_user_tenant_dept`(`tenant_id` ASC, `dept_id` ASC) USING BTREE,
  INDEX `idx_sys_user_tenant_mobile`(`tenant_id` ASC, `mobile` ASC) USING BTREE,
  INDEX `idx_sys_user_tenant_email`(`tenant_id` ASC, `email` ASC) USING BTREE,
  INDEX `idx_sys_user_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1603 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户账号表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'platform', 1, 'admin', '$2b$10$3yP0opk2EICLIv2T8mxlH.wZC9XlhDY6/bVVBp5a9vtRmXjv/KbQK', 1, 1, '2026-03-20 08:09:53', NULL, NULL, NULL, '2026-04-05 22:24:25', NULL, 'admin', 0, '2026-04-05 14:24:25', '127.0.0.1', '2026-03-23 16:49:04');
INSERT INTO `sys_user` VALUES (2, 'tenant-a', 2, 'auditor', '$2b$10$3yP0opk2EICLIv2T8mxlH.wZC9XlhDY6/bVVBp5a9vtRmXjv/KbQK', 1, 1, '2026-03-20 08:09:53', NULL, NULL, NULL, '2026-03-23 23:56:03', NULL, 'auditor', 0, '2026-03-31 05:39:33', '127.0.0.1', NULL);

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_user_role_tenant_user_role`(`tenant_id` ASC, `user_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_sys_user_role_tenant_user`(`tenant_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_sys_user_role_tenant_role`(`tenant_id` ASC, `role_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 46 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 'platform', 1, 1, '2026-03-20 08:09:53', NULL, NULL, '2026-03-20 08:09:55');
INSERT INTO `sys_user_role` VALUES (2, 'tenant-a', 2, 2, '2026-03-20 08:09:53', NULL, NULL, '2026-03-20 08:09:55');

SET FOREIGN_KEY_CHECKS = 1;
