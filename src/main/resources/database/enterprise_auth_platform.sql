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

 Date: 23/03/2026 16:51:52
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET @OLD_TIME_ZONE = @@TIME_ZONE;
SET TIME_ZONE = '+00:00';

-- ----------------------------
-- Table structure for oauth2_authorization
-- ----------------------------
DROP TABLE IF EXISTS `oauth2_authorization`;
CREATE TABLE `oauth2_authorization`  (
  `id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权记录 ID',
  `registered_client_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '注册客户端 ID',
  `principal_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权主体名称',
  `authorization_grant_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权类型',
  `authorized_scopes` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '已授权作用域列表',
  `attributes` blob NULL COMMENT '授权上下文属性序列化数据',
  `state` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'OAuth2 state 参数',
  `authorization_code_value` blob NULL COMMENT '授权码值',
  `authorization_code_issued_at` timestamp NULL DEFAULT NULL COMMENT '授权码签发时间',
  `authorization_code_expires_at` timestamp NULL DEFAULT NULL COMMENT '授权码过期时间',
  `authorization_code_metadata` blob NULL COMMENT '授权码元数据',
  `access_token_value` blob NULL COMMENT '访问令牌值',
  `access_token_issued_at` timestamp NULL DEFAULT NULL COMMENT '访问令牌签发时间',
  `access_token_expires_at` timestamp NULL DEFAULT NULL COMMENT '访问令牌过期时间',
  `access_token_metadata` blob NULL COMMENT '访问令牌元数据',
  `access_token_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '访问令牌类型',
  `access_token_scopes` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '访问令牌作用域列表',
  `oidc_id_token_value` blob NULL COMMENT 'OIDC ID Token 值',
  `oidc_id_token_issued_at` timestamp NULL DEFAULT NULL COMMENT 'OIDC ID Token 签发时间',
  `oidc_id_token_expires_at` timestamp NULL DEFAULT NULL COMMENT 'OIDC ID Token 过期时间',
  `oidc_id_token_metadata` blob NULL COMMENT 'OIDC ID Token 元数据',
  `refresh_token_value` blob NULL COMMENT '刷新令牌值',
  `refresh_token_issued_at` timestamp NULL DEFAULT NULL COMMENT '刷新令牌签发时间',
  `refresh_token_expires_at` timestamp NULL DEFAULT NULL COMMENT '刷新令牌过期时间',
  `refresh_token_metadata` blob NULL COMMENT '刷新令牌元数据',
  `user_code_value` blob NULL COMMENT '设备授权用户验证码值',
  `user_code_issued_at` timestamp NULL DEFAULT NULL COMMENT '设备授权用户验证码签发时间',
  `user_code_expires_at` timestamp NULL DEFAULT NULL COMMENT '设备授权用户验证码过期时间',
  `user_code_metadata` blob NULL COMMENT '设备授权用户验证码元数据',
  `device_code_value` blob NULL COMMENT '设备授权设备码值',
  `device_code_issued_at` timestamp NULL DEFAULT NULL COMMENT '设备授权设备码签发时间',
  `device_code_expires_at` timestamp NULL DEFAULT NULL COMMENT '设备授权设备码过期时间',
  `device_code_metadata` blob NULL COMMENT '设备授权设备码元数据',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'OAuth2 授权记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of oauth2_authorization
-- ----------------------------

-- ----------------------------
-- Table structure for oauth2_authorization_consent
-- ----------------------------
DROP TABLE IF EXISTS `oauth2_authorization_consent`;
CREATE TABLE `oauth2_authorization_consent`  (
  `registered_client_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '注册客户端 ID',
  `principal_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权主体名称',
  `authorities` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户已同意的权限集合',
  PRIMARY KEY (`registered_client_id`, `principal_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'OAuth2 授权同意记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of oauth2_authorization_consent
-- ----------------------------

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
INSERT INTO `sys_audit_export_policy` VALUES (1, 'platform', 9, 120, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');

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
) ENGINE = InnoDB AUTO_INCREMENT = 33 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '审计导出任务表' ROW_FORMAT = DYNAMIC;

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
) ENGINE = InnoDB AUTO_INCREMENT = 472 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '安全审计日志表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_audit_log
-- ----------------------------

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
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统分类规则表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_category_rule
-- ----------------------------
INSERT INTO `sys_category_rule` VALUES (1, 'platform', 'dict', 'oauth', 'OAuth 字典', 'oauth.*,scope.*', 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_category_rule` VALUES (2, 'platform', 'dict', 'user', '用户域字典', 'user.*,dept.*,role.*', 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_category_rule` VALUES (3, 'platform', 'config', 'auth', '认证参数', 'auth.*,oauth.*', 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_category_rule` VALUES (4, 'platform', 'config', 'platform', '平台参数', 'tenant.*,system.*,feature.*', 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');

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
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (1, 'platform', 'login.captcha.enabled', 'true', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:54', NULL, NULL, 0);
INSERT INTO `sys_config` VALUES (2, 'platform', 'system.category.dict.oauth', 'oauth.*,scope.*', '2026-03-20 20:12:50', 'OAuth 字典', '2026-03-20 20:12:50', 'system', 'system', 0);
INSERT INTO `sys_config` VALUES (3, 'platform', 'system.category.dict.user', 'user.*,dept.*,role.*', '2026-03-20 20:12:50', '用户域字典', '2026-03-20 20:12:50', 'system', 'system', 0);
INSERT INTO `sys_config` VALUES (4, 'platform', 'system.category.config.auth', 'auth.*,oauth.*', '2026-03-20 20:12:50', '认证参数', '2026-03-20 20:12:50', 'system', 'system', 0);
INSERT INTO `sys_config` VALUES (5, 'platform', 'system.category.config.platform', 'tenant.*,system.*,feature.*', '2026-03-20 20:12:50', '平台参数', '2026-03-20 20:12:50', 'system', 'system', 0);

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
) ENGINE = InnoDB AUTO_INCREMENT = 183 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '部门树表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO `sys_dept` VALUES (1, 'platform', NULL, '平台运营中心', '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (2, 'tenant-a', NULL, '租户A-财务部', '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (3, 'tenant-a', NULL, '租户A-研发部', '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (4, 'platform', NULL, 'Platform Operation Center', '2026-03-20 00:34:56', NULL, NULL, '2026-03-20 00:34:56', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (5, 'tenant-a', NULL, 'Tenant A Finance', '2026-03-20 00:34:56', NULL, NULL, '2026-03-20 00:34:56', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (6, 'tenant-a', NULL, 'Tenant A R&D', '2026-03-20 00:34:56', NULL, NULL, '2026-03-20 00:34:56', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (9, 'platform', NULL, '测试部门', '2026-03-20 14:30:50', 'TEST_DEPT_19851573835200', NULL, '2026-03-20 14:30:49', 'system', 'system', 1);
INSERT INTO `sys_dept` VALUES (153, 'platform', NULL, '测试部门', '2026-03-23 16:45:19', 'TEST_DEPT_15839912323000', NULL, '2026-03-23 16:45:19', 'system', 'system', 1);
INSERT INTO `sys_dept` VALUES (169, 'platform', NULL, '测试部门', '2026-03-23 16:49:13', 'TEST_DEPT_16073431213700', NULL, '2026-03-23 16:49:12', 'system', 'system', 1);

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
) ENGINE = InnoDB AUTO_INCREMENT = 159 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_dict
-- ----------------------------
INSERT INTO `sys_dict` VALUES (1, 'platform', 'user_status', 'ENABLED', '启用', '2026-03-20 00:09:53', '2026-03-20 00:09:54', NULL, NULL, 0);
INSERT INTO `sys_dict` VALUES (6, 'platform', 'demo', 'k19853731725700', 'v', '2026-03-20 14:30:52', '2026-03-20 14:30:52', 'system', 'system', 1);
INSERT INTO `sys_dict` VALUES (137, 'platform', 'demo', 'k15843632237600', 'v', '2026-03-23 16:45:23', '2026-03-23 16:45:22', 'system', 'system', 1);
INSERT INTO `sys_dict` VALUES (158, 'platform', 'demo', 'k16077250144100', 'v', '2026-03-23 16:49:17', '2026-03-23 16:49:16', 'system', 'system', 1);

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
INSERT INTO `sys_notice` VALUES (1, 'platform', '首期骨架已启用', '系统管理模块的公告、字典、参数基础能力已初始化。', 1, '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:54', NULL, NULL, 0);

-- ----------------------------
-- Table structure for sys_oauth_client
-- ----------------------------
DROP TABLE IF EXISTS `sys_oauth_client`;
CREATE TABLE `sys_oauth_client`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `client_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OAuth2 客户端 ID',
  `client_secret` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OAuth2 客户端密钥',
  `client_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端名称',
  `redirect_uris` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '重定向地址列表，逗号分隔',
  `scopes` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域列表，逗号分隔',
  `grant_types` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权类型列表，逗号分隔',
  `require_pkce` tinyint NOT NULL DEFAULT 0 COMMENT '是否要求 PKCE',
  `require_consent` tinyint NOT NULL DEFAULT 0 COMMENT '是否要求授权确认页',
  `client_status` tinyint NOT NULL DEFAULT 1 COMMENT '客户端状态：1 启用，0 禁用',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记，0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_oauth_client_client_id`(`client_id` ASC) USING BTREE,
  INDEX `idx_sys_oauth_client_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 68 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'OAuth2 客户端配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_oauth_client
-- ----------------------------
INSERT INTO `sys_oauth_client` VALUES (1, 'platform', 'eap-web', '$2a$10$E474.s82IHizGWwCiiPG4.b0wjFuSFAULOvHzxIfooIL6tuVRYNk6', '企业权限管理平台管理端', 'http://127.0.0.1:8080/swagger-ui/oauth2-redirect.html', 'openid,profile,api.read,api.write', 'authorization_code,refresh_token,client_credentials', 0, 1, 1, '2026-03-20 14:19:00', '2026-03-20 14:19:00', 'system', 'system', 0);
INSERT INTO `sys_oauth_client` VALUES (2, 'platform', 'eap-frontend-spa', '', 'Frontend Console', 'http://127.0.0.1:5173/auth/callback,http://localhost:5173/auth/callback', 'openid,profile,api.read,api.write', 'authorization_code,refresh_token', 1, 1, 1, '2026-03-20 14:21:09', '2026-03-20 14:21:09', 'system', 'system', 0);

-- ----------------------------
-- Table structure for sys_oauth_client_history
-- ----------------------------
DROP TABLE IF EXISTS `sys_oauth_client_history`;
CREATE TABLE `sys_oauth_client_history`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `client_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端编号',
  `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型',
  `summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件摘要',
  `payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '事件负载',
  `operator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '操作人',
  `occurred_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_oauth_client_history_tenant_client_time`(`tenant_id` ASC, `client_id` ASC, `occurred_at` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 31 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'OAuth2 客户端状态历史表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_oauth_client_history
-- ----------------------------

-- ----------------------------
-- Table structure for sys_oauth_scope
-- ----------------------------
DROP TABLE IF EXISTS `sys_oauth_scope`;
CREATE TABLE `sys_oauth_scope`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `scope_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域编码',
  `scope_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域名称',
  `scope_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作用域说明',
  `scope_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作用域类型',
  `default_selected` tinyint NOT NULL DEFAULT 0 COMMENT '默认是否选中',
  `visible_in_consent` tinyint NOT NULL DEFAULT 1 COMMENT '是否在同意页展示',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_oauth_scope_tenant_code`(`tenant_id` ASC, `scope_code` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'OAuth2 作用域定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_oauth_scope
-- ----------------------------
INSERT INTO `sys_oauth_scope` VALUES (1, 'platform', 'openid', 'OpenID', '读取用户基础身份信息，用于建立统一登录会话。', 'IDENTITY', 1, 1, 10, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_oauth_scope` VALUES (2, 'platform', 'profile', '用户资料', '读取用户资料信息，用于展示昵称、头像等基础资料。', 'PROFILE', 1, 1, 20, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_oauth_scope` VALUES (3, 'platform', 'api.read', '接口读取', '接口读取：允许读取平台接口与管理数据。', 'API', 1, 1, 30, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_oauth_scope` VALUES (4, 'platform', 'api.write', '接口写入', '接口写入：允许创建、修改或删除平台业务数据。', 'API', 0, 1, 40, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');

-- ----------------------------
-- Table structure for sys_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `resource_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源编码',
  `action_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作编码',
  `scope_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域编码',
  `permission_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '扁平化权限编码',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `permission_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '权限名称',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_permission_tenant_code`(`tenant_id` ASC, `permission_code` ASC) USING BTREE,
  INDEX `idx_sys_permission_tenant_resource`(`tenant_id` ASC, `resource_code` ASC, `action_code` ASC) USING BTREE,
  INDEX `idx_sys_permission_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 33 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_permission
-- ----------------------------
INSERT INTO `sys_permission` VALUES (1, 'platform', 'auth', 'read', 'tenant', 'auth:read', '2026-03-20 00:09:53', '认证读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (2, 'platform', 'auth', 'write', 'tenant', 'auth:write', '2026-03-20 00:09:53', '认证写入', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (3, 'platform', 'user', 'read', 'tenant', 'user:read', '2026-03-20 00:09:53', '用户读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (4, 'platform', 'user', 'write', 'tenant', 'user:write', '2026-03-20 00:09:53', '用户写入', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (5, 'platform', 'role', 'read', 'tenant', 'role:read', '2026-03-20 00:09:53', '角色读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (6, 'platform', 'role', 'write', 'tenant', 'role:write', '2026-03-20 00:09:53', '角色写入', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (7, 'platform', 'permission', 'read', 'tenant', 'permission:read', '2026-03-20 00:09:53', '权限读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (8, 'platform', 'permission', 'write', 'tenant', 'permission:write', '2026-03-20 00:09:53', '权限写入', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (9, 'platform', 'dept', 'read', 'tenant', 'dept:read', '2026-03-20 00:09:53', '部门读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (10, 'platform', 'dept', 'write', 'tenant', 'dept:write', '2026-03-20 00:09:53', '部门写入', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (11, 'platform', 'tenant', 'read', 'platform', 'tenant:read', '2026-03-20 00:09:53', '租户读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (12, 'platform', 'tenant', 'write', 'platform', 'tenant:write', '2026-03-20 00:09:53', '租户写入', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (13, 'platform', 'audit', 'read', 'tenant', 'audit:read', '2026-03-20 00:09:53', '审计读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (14, 'platform', 'audit', 'write', 'tenant', 'audit:write', '2026-03-20 00:09:53', '审计写入', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (15, 'platform', 'system', 'read', 'tenant', 'system:read', '2026-03-20 00:09:53', '系统设施读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (16, 'platform', 'system', 'write', 'tenant', 'system:write', '2026-03-20 00:09:53', '系统设施写入', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (17, 'platform', 'session', 'write', 'tenant', 'session:write', '2026-03-20 00:09:53', '会话管控(下线等)', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (18, 'tenant-a', 'audit', 'read', 'tenant', 'audit:read', '2026-03-20 00:09:53', '审计读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (19, 'tenant-a', 'user', 'read', 'tenant', 'user:read', '2026-03-20 00:09:53', '用户读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (20, 'tenant-a', 'permission', 'read', 'tenant', 'permission:read', '2026-03-20 00:09:53', '权限读取', '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (21, 'platform', 'demo', 'write', 'tenant', 'demo:write:19852792270800', '2026-03-20 14:30:51', '演示写入', '2026-03-20 14:30:51', 'system', 'system', 1);
INSERT INTO `sys_permission` VALUES (31, 'platform', 'demo', 'write', 'tenant', 'demo:write:15841153872200', '2026-03-23 16:45:20', '演示写入', '2026-03-23 16:45:20', 'system', 'system', 1);
INSERT INTO `sys_permission` VALUES (32, 'platform', 'demo', 'write', 'tenant', 'demo:write:16074618498600', '2026-03-23 16:49:14', '演示写入', '2026-03-23 16:49:13', 'system', 'system', 1);

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
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_role_tenant_code`(`tenant_id` ASC, `role_code` ASC) USING BTREE,
  INDEX `idx_sys_role_tenant_scope`(`tenant_id` ASC, `data_scope_type` ASC) USING BTREE,
  INDEX `idx_sys_role_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, 'platform', 'ADMIN', '平台管理员', 'ALL', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_role` VALUES (2, 'tenant-a', 'AUDITOR', '审计员', 'DEPT', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);

-- ----------------------------
-- Table structure for sys_role_dept_scope
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_dept_scope`;
CREATE TABLE `sys_role_dept_scope`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_role_dept_scope_tenant_role_dept`(`tenant_id` ASC, `role_id` ASC, `dept_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色自定义部门范围表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role_dept_scope
-- ----------------------------

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户标识',
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `permission_id` bigint NOT NULL COMMENT '权限 ID',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_role_permission_tenant_role_permission`(`tenant_id` ASC, `role_id` ASC, `permission_id` ASC) USING BTREE,
  INDEX `idx_sys_role_permission_tenant_role`(`tenant_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_sys_role_permission_tenant_permission`(`tenant_id` ASC, `permission_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 21 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role_permission
-- ----------------------------
INSERT INTO `sys_role_permission` VALUES (1, 'platform', 1, 1, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (2, 'platform', 1, 2, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (3, 'platform', 1, 3, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (4, 'platform', 1, 4, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (5, 'platform', 1, 5, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (6, 'platform', 1, 6, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (7, 'platform', 1, 7, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (8, 'platform', 1, 8, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (9, 'platform', 1, 9, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (10, 'platform', 1, 10, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (11, 'platform', 1, 11, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (12, 'platform', 1, 12, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (13, 'platform', 1, 13, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (14, 'platform', 1, 14, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (15, 'platform', 1, 15, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (16, 'platform', 1, 16, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (17, 'platform', 1, 17, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (18, 'tenant-a', 2, 18, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (19, 'tenant-a', 2, 19, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_role_permission` VALUES (20, 'tenant-a', 2, 20, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');

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
INSERT INTO `sys_tenant` VALUES (1, 'platform', '平台租户', 1, '2026-03-20 00:09:53', 1, NULL, 'platform-governance', '负责全局治理与租户运维', '2026-03-20 22:26:06', NULL, NULL, 0);
INSERT INTO `sys_tenant` VALUES (2, 'tenant-a', '租户A', 0, '2026-03-20 00:09:53', 1, NULL, 'business-standard', '默认标准业务租户', '2026-03-20 22:26:06', NULL, NULL, 0);

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
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户能力定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_capability
-- ----------------------------
INSERT INTO `sys_tenant_capability` VALUES (1, 'platform', 'oauth', '统一认证', '统一认证、授权码登录与开放接入能力', 10, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_capability` VALUES (2, 'platform', 'user', '用户管理', '用户目录、启停、角色分配与组织可见范围管理', 20, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_capability` VALUES (3, 'platform', 'role', '角色授权', '角色模型、权限树与数据范围授权', 30, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_capability` VALUES (4, 'platform', 'dept', '组织管理', '组织树、负责人和部门层级治理', 40, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_capability` VALUES (5, 'platform', 'tenant', '租户治理', '租户套餐、能力配置与生命周期治理', 50, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_capability` VALUES (6, 'platform', 'system', '系统管理', '字典、参数、公告与分类配置管理', 60, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_capability` VALUES (7, 'platform', 'audit', '安全审计', '审计查询、导出与授权记录联动', 70, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_capability` VALUES (8, 'platform', 'notice', '通知公告', '公告发布与租户通知能力', 80, 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');

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
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户能力覆盖表' ROW_FORMAT = DYNAMIC;

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
) ENGINE = InnoDB AUTO_INCREMENT = 35 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户变更记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_change_log
-- ----------------------------
INSERT INTO `sys_tenant_change_log` VALUES (23, 'tenant-a', 'CAPABILITY', 'capabilityOverrides', NULL, 'audit:true:审计导出与看板能力|notice:false:null', '更新租户能力覆盖', 'tester', '2026-03-23 16:45:24');
INSERT INTO `sys_tenant_change_log` VALUES (32, 'tenant-a', 'CAPABILITY', 'capabilityOverrides', NULL, 'audit:true:审计导出与看板能力|notice:false:null', '更新租户能力覆盖', 'tester', '2026-03-23 16:49:17');

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
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户套餐定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_package
-- ----------------------------
INSERT INTO `sys_tenant_package` VALUES (1, 'platform', 'platform-governance', '平台治理版', 9999, 1024, '负责全局治理与租户运维', 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_package` VALUES (2, 'platform', 'business-standard', '标准版', 200, 200, '适用于常规业务租户', 1, 'system', 'system', 0, '2026-03-20 22:26:06', '2026-03-20 22:26:06');

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
) ENGINE = InnoDB AUTO_INCREMENT = 13 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户套餐能力关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_tenant_package_capability
-- ----------------------------
INSERT INTO `sys_tenant_package_capability` VALUES (1, 'platform', 'platform-governance', 'oauth', 'system', 'system', '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (2, 'platform', 'platform-governance', 'audit', 'system', 'system', '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (3, 'platform', 'platform-governance', 'system', 'system', 'system', '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (4, 'platform', 'platform-governance', 'tenant', 'system', 'system', '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (5, 'platform', 'business-standard', 'user', 'system', 'system', '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (6, 'platform', 'business-standard', 'role', 'system', 'system', '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (7, 'platform', 'business-standard', 'audit', 'system', 'system', '2026-03-20 22:26:06', '2026-03-20 22:26:06');
INSERT INTO `sys_tenant_package_capability` VALUES (8, 'platform', 'business-standard', 'notice', 'system', 'system', '2026-03-20 22:26:06', '2026-03-20 22:26:06');

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
  UNIQUE INDEX `uk_sys_user_tenant_username`(`tenant_id` ASC, `username` ASC) USING BTREE,
  INDEX `idx_sys_user_tenant_dept`(`tenant_id` ASC, `dept_id` ASC) USING BTREE,
  INDEX `idx_sys_user_tenant_mobile`(`tenant_id` ASC, `mobile` ASC) USING BTREE,
  INDEX `idx_sys_user_tenant_email`(`tenant_id` ASC, `email` ASC) USING BTREE,
  INDEX `idx_sys_user_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 627 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户账号表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'platform', 1, 'admin', '$2b$10$3yP0opk2EICLIv2T8mxlH.wZC9XlhDY6/bVVBp5a9vtRmXjv/KbQK', 1, 1, '2026-03-20 00:09:53', NULL, NULL, NULL, '2026-03-23 15:56:02', NULL, 'test', 0, '2026-03-23 16:49:09', '127.0.0.1', '2026-03-23 16:49:04');
INSERT INTO `sys_user` VALUES (2, 'tenant-a', 2, 'auditor', '$2b$10$3yP0opk2EICLIv2T8mxlH.wZC9XlhDY6/bVVBp5a9vtRmXjv/KbQK', 1, 1, '2026-03-20 00:09:53', NULL, NULL, NULL, '2026-03-23 15:56:03', NULL, NULL, 0, NULL, NULL, NULL);

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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 'platform', 1, 1, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_user_role` VALUES (2, 'tenant-a', 2, 2, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');

SET FOREIGN_KEY_CHECKS = 1;
SET TIME_ZONE = @OLD_TIME_ZONE;
