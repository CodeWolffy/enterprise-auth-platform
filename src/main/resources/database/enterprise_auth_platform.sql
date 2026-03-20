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

 Date: 20/03/2026 00:36:58
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '安全审计日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_audit_log
-- ----------------------------

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
  INDEX `idx_sys_config_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (1, 'platform', 'login.captcha.enabled', 'true', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:54', NULL, NULL, 0);

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
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '部门树表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO `sys_dept` VALUES (1, 'platform', NULL, '平台运营中心', '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (2, 'tenant-a', NULL, '租户A-财务部', '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (3, 'tenant-a', NULL, '租户A-研发部', '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (4, 'platform', NULL, 'Platform Operation Center', '2026-03-20 00:34:56', NULL, NULL, '2026-03-20 00:34:56', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (5, 'tenant-a', NULL, 'Tenant A Finance', '2026-03-20 00:34:56', NULL, NULL, '2026-03-20 00:34:56', NULL, NULL, 0);
INSERT INTO `sys_dept` VALUES (6, 'tenant-a', NULL, 'Tenant A R&D', '2026-03-20 00:34:56', NULL, NULL, '2026-03-20 00:34:56', NULL, NULL, 0);

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
  INDEX `idx_sys_dict_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_dict
-- ----------------------------
INSERT INTO `sys_dict` VALUES (1, 'platform', 'user_status', 'ENABLED', '启用', '2026-03-20 00:09:53', '2026-03-20 00:09:54', NULL, NULL, 0);

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
  INDEX `idx_sys_notice_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通知公告表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_notice
-- ----------------------------
INSERT INTO `sys_notice` VALUES (1, 'platform', '首期骨架已启用', '系统管理模块的公告、字典、参数基础能力已初始化。', 1, '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:54', NULL, NULL, 0);

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
) ENGINE = InnoDB AUTO_INCREMENT = 21 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_permission
-- ----------------------------
INSERT INTO `sys_permission` VALUES (1, 'platform', 'auth', 'read', 'tenant', 'auth:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (2, 'platform', 'auth', 'write', 'tenant', 'auth:write', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (3, 'platform', 'user', 'read', 'tenant', 'user:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (4, 'platform', 'user', 'write', 'tenant', 'user:write', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (5, 'platform', 'role', 'read', 'tenant', 'role:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (6, 'platform', 'role', 'write', 'tenant', 'role:write', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (7, 'platform', 'permission', 'read', 'tenant', 'permission:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (8, 'platform', 'permission', 'write', 'tenant', 'permission:write', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (9, 'platform', 'dept', 'read', 'tenant', 'dept:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (10, 'platform', 'dept', 'write', 'tenant', 'dept:write', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (11, 'platform', 'tenant', 'read', 'platform', 'tenant:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (12, 'platform', 'tenant', 'write', 'platform', 'tenant:write', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (13, 'platform', 'audit', 'read', 'tenant', 'audit:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (14, 'platform', 'audit', 'write', 'tenant', 'audit:write', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (15, 'platform', 'system', 'read', 'tenant', 'system:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (16, 'platform', 'system', 'write', 'tenant', 'system:write', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (17, 'platform', 'session', 'write', 'tenant', 'session:write', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (18, 'tenant-a', 'audit', 'read', 'tenant', 'audit:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (19, 'tenant-a', 'user', 'read', 'tenant', 'user:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_permission` VALUES (20, 'tenant-a', 'permission', 'read', 'tenant', 'permission:read', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);

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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色定义表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, 'platform', 'ADMIN', '平台管理员', 'ALL', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_role` VALUES (2, 'tenant-a', 'AUDITOR', '审计员', 'DEPT', '2026-03-20 00:09:53', NULL, '2026-03-20 00:09:53', NULL, NULL, 0);

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
) ENGINE = InnoDB AUTO_INCREMENT = 21 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联表' ROW_FORMAT = Dynamic;

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
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `tenant_id`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_sys_tenant_status`(`tenant_status` ASC) USING BTREE,
  INDEX `idx_sys_tenant_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户主表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_tenant
-- ----------------------------
INSERT INTO `sys_tenant` VALUES (1, 'platform', '平台租户', 1, '2026-03-20 00:09:53', 1, NULL, '2026-03-20 00:09:53', NULL, NULL, 0);
INSERT INTO `sys_tenant` VALUES (2, 'tenant-a', '租户A', 0, '2026-03-20 00:09:53', 1, NULL, '2026-03-20 00:09:53', NULL, NULL, 0);

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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户账号表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 'platform', 1, 'admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6M6jwxKF1uHmIiMaTZGi99ZTSdK6W', 1, 1, '2026-03-20 00:09:53', NULL, NULL, NULL, '2026-03-20 00:09:53', NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO `sys_user` VALUES (2, 'tenant-a', 2, 'auditor', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6M6jwxKF1uHmIiMaTZGi99ZTSdK6W', 1, 1, '2026-03-20 00:09:53', NULL, NULL, NULL, '2026-03-20 00:09:53', NULL, NULL, 0, NULL, NULL, NULL);

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
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 'platform', 1, 1, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');
INSERT INTO `sys_user_role` VALUES (2, 'tenant-a', 2, 2, '2026-03-20 00:09:53', NULL, NULL, '2026-03-20 00:09:55');

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
  UNIQUE INDEX `uk_sys_oauth_client_tenant_client_id`(`tenant_id` ASC, `client_id` ASC) USING BTREE,
  INDEX `idx_sys_oauth_client_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'OAuth2 客户端配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sys_oauth_client
-- ----------------------------
INSERT INTO `sys_oauth_client` VALUES (1, 'platform', 'eap-web', '$2a$10$babwhNwwnHRe.kleYGFsdeaIE0O544FkZ7VpQAJQIvx1QOBcx3yL.', '企业权限平台管理端', 'http://127.0.0.1:8080/swagger-ui/oauth2-redirect.html', 'openid,profile,api.read,api.write', 'authorization_code,refresh_token,client_credentials', 0, 0, 1, NOW(), NOW(), 'system', 'system', 0);

SET FOREIGN_KEY_CHECKS = 1;
