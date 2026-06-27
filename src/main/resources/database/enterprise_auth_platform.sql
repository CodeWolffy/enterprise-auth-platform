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

 Date: 27/06/2026 10:43:16
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for codegen_data_source
-- ----------------------------
DROP TABLE IF EXISTS `codegen_data_source`;
CREATE TABLE `codegen_data_source`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据源名称',
  `jdbc_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'JDBC 地址',
  `username` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户名',
  `password_cipher` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '密码密文',
  `db_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '数据库名',
  `host` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '主机',
  `port` int NULL DEFAULT NULL COMMENT '端口',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `external_authorized` tinyint NOT NULL DEFAULT 0 COMMENT '外部数据源是否已显式授权',
  `authorized_at` timestamp NULL DEFAULT NULL COMMENT '外部数据源授权时间',
  `authorization_note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '外部数据源授权说明',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_codegen_data_source_tenant_name`(`tenant_id` ASC, `name` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_codegen_data_source_tenant_enabled`(`tenant_id` ASC, `enabled` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码生成数据源' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for codegen_table
-- ----------------------------
DROP TABLE IF EXISTS `codegen_table`;
CREATE TABLE `codegen_table`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `data_source_id` bigint NOT NULL COMMENT '数据源 ID',
  `table_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表名',
  `table_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '表注释',
  `class_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '类名',
  `tpl_category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'crud' COMMENT '模板分类',
  `package_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '包名',
  `module_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模块名',
  `business_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '业务名',
  `function_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '功能名',
  `function_author` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '作者',
  `gen_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'preview' COMMENT '生成方式',
  `gen_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '生成路径',
  `options` json NULL COMMENT '扩展选项',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_codegen_table_tenant_ds_table`(`tenant_id` ASC, `data_source_id` ASC, `table_name` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_codegen_table_tenant_table`(`tenant_id` ASC, `table_name` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码生成表配置' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for codegen_table_column
-- ----------------------------
DROP TABLE IF EXISTS `codegen_table_column`;
CREATE TABLE `codegen_table_column`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `table_id` bigint NOT NULL COMMENT '表配置 ID',
  `column_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段名',
  `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '字段注释',
  `column_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '字段类型',
  `data_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '数据类型',
  `java_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'Java 类型',
  `java_field` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'Java 字段',
  `is_pk` tinyint NOT NULL DEFAULT 0 COMMENT '是否主键',
  `is_required` tinyint NOT NULL DEFAULT 0 COMMENT '是否必填',
  `is_insert` tinyint NOT NULL DEFAULT 1 COMMENT '是否插入',
  `is_edit` tinyint NOT NULL DEFAULT 1 COMMENT '是否编辑',
  `is_list` tinyint NOT NULL DEFAULT 1 COMMENT '是否列表',
  `is_query` tinyint NOT NULL DEFAULT 0 COMMENT '是否查询',
  `query_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'EQ' COMMENT '查询方式',
  `html_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'input' COMMENT '表单控件',
  `dict_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '字典类型',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_codegen_column_table_column`(`tenant_id` ASC, `table_id` ASC, `column_name` ASC) USING BTREE,
  INDEX `idx_codegen_column_table_sort`(`tenant_id` ASC, `table_id` ASC, `sort` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 91 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码生成字段配置' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for codegen_template
-- ----------------------------
DROP TABLE IF EXISTS `codegen_template`;
CREATE TABLE `codegen_template`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `language` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '语言：java/typescript/vue',
  `template_category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'backend' COMMENT '模板分类：backend/frontend/api/type/view',
  `path_pattern` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '匹配生成路径的正则或关键字',
  `content` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板内容（{{className}} 等占位）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '描述',
  `builtin` tinyint NOT NULL DEFAULT 0 COMMENT '是否内置（不可删除）',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_codegen_template_tenant_name`(`tenant_id` ASC, `name` ASC) USING BTREE,
  INDEX `idx_codegen_template_tenant_lang_pattern`(`tenant_id` ASC, `language` ASC, `path_pattern` ASC) USING BTREE,
  INDEX `idx_codegen_template_tenant_category`(`tenant_id` ASC, `template_category` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码生成自定义模板' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for flyway_schema_history
-- ----------------------------
DROP TABLE IF EXISTS `flyway_schema_history`;
CREATE TABLE `flyway_schema_history`  (
  `installed_rank` int NOT NULL,
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `script` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `checksum` int NULL DEFAULT NULL,
  `installed_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`) USING BTREE,
  INDEX `flyway_schema_history_s_idx`(`success` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

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
) ENGINE = InnoDB AUTO_INCREMENT = 90 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统分类规则表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_codegen_allowlist
-- ----------------------------
DROP TABLE IF EXISTS `sys_codegen_allowlist`;
CREATE TABLE `sys_codegen_allowlist`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `table_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '允许生成的数据表名',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '说明',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_codegen_allowlist_tenant_table`(`tenant_id` ASC, `table_name` ASC) USING BTREE,
  INDEX `idx_codegen_allowlist_tenant_enabled`(`tenant_id` ASC, `enabled` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 177 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码生成表白名单' ROW_FORMAT = DYNAMIC;

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
) ENGINE = InnoDB AUTO_INCREMENT = 2170 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户配置表' ROW_FORMAT = DYNAMIC;

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
  `leader_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人姓名',
  `leader_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人电话',
  `order_no` int NOT NULL DEFAULT 0 COMMENT '排序序号',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态：0 停用，1 启用',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_dept_tenant_parent`(`tenant_id` ASC, `parent_id` ASC) USING BTREE,
  INDEX `idx_sys_dept_tenant_code`(`tenant_id` ASC, `dept_code` ASC) USING BTREE,
  INDEX `idx_sys_dept_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_sys_dept_tenant_status_order`(`tenant_id` ASC, `deleted` ASC, `enabled` ASC, `order_no` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2826 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '部门树表' ROW_FORMAT = DYNAMIC;

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
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '字典类型说明',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态：0 停用，1 启用',
  `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_dict_tenant_type`(`tenant_id` ASC, `dict_type` ASC) USING BTREE,
  INDEX `idx_sys_dict_tenant_type`(`tenant_id` ASC, `dict_type` ASC) USING BTREE,
  INDEX `idx_sys_dict_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_sys_dict_tenant_deleted_created_at`(`tenant_id` ASC, `deleted` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_sys_dict_tenant_deleted_created_by`(`tenant_id` ASC, `deleted` ASC, `created_by` ASC) USING BTREE,
  INDEX `idx_sys_dict_tenant_enabled_updated`(`tenant_id` ASC, `deleted` ASC, `enabled` ASC, `updated_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2540 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_dict_value
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_value`;
CREATE TABLE `sys_dict_value`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `dict_id` bigint NOT NULL COMMENT '字典主键，关联 sys_dict.id',
  `dict_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典类型冗余，加速查询',
  `dict_label` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典标签（显示名）',
  `dict_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典键值',
  `show_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '回显样式',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序序号',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用 (0禁用 1启用)',
  `remarks` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_dict_value_tenant_dict_value`(`tenant_id` ASC, `dict_id` ASC, `dict_value` ASC) USING BTREE,
  INDEX `idx_sys_dict_value_tenant_type`(`tenant_id` ASC, `dict_type` ASC) USING BTREE,
  INDEX `idx_sys_dict_value_tenant_dict`(`tenant_id` ASC, `dict_id` ASC) USING BTREE,
  INDEX `idx_sys_dict_value_tenant_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 599 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典值表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `operator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `request_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `client_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `location` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `method` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `request_uri` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `request_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `request_time` bigint NULL DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `ex_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_request_id`(`request_id` ASC) USING BTREE,
  INDEX `idx_tenant_created`(`tenant_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_operator_created`(`operator` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_event_type_created`(`event_type` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 144 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '操作日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_login_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `ip_addr` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `location` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `browser` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `os` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_created`(`tenant_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_user_name_created`(`user_name` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_status_created`(`status` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 77 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '登录日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_mail_channel
-- ----------------------------
DROP TABLE IF EXISTS `sys_mail_channel`;
CREATE TABLE `sys_mail_channel`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CUSTOM' COMMENT '渠道类型：QQ|NETEASE|GMAIL|OUTLOOK|CUSTOM',
  `mail_host` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SMTP 服务器地址',
  `mail_port` int NOT NULL DEFAULT 587 COMMENT 'SMTP 端口',
  `mail_username` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SMTP 用户名/邮箱地址',
  `mail_password` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SMTP 密码或授权码密文/原文',
  `mail_from` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发件人地址',
  `mail_protocol` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'smtp' COMMENT '邮件协议',
  `use_ssl` tinyint NOT NULL DEFAULT 0 COMMENT '是否使用 SSL',
  `use_starttls` tinyint NOT NULL DEFAULT 1 COMMENT '是否使用 STARTTLS',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_mail_channel_tenant`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_sys_mail_channel_enabled`(`tenant_id` ASC, `enabled` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '邮件渠道配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '父节点 ID',
  `name` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '菜单名称',
  `permission` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '菜单权限',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前端路由路径',
  `component` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前端组件名称',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '类型：0=菜单；1=按钮',
  `redirect` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '重定向路径',
  `icon` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '图标标识',
  `outer_status` tinyint NOT NULL DEFAULT 0 COMMENT '外链状态 (0否 1是)',
  `application_key` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '应用标识，多应用隔离',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常；1=删除',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 (0未删除 1已删除)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_menu_parent`(`parent_id` ASC) USING BTREE,
  INDEX `idx_sys_menu_type`(`type` ASC) USING BTREE,
  INDEX `idx_sys_menu_permission`(`permission` ASC) USING BTREE,
  INDEX `idx_sys_menu_del_flag`(`del_flag` ASC) USING BTREE,
  INDEX `idx_sys_menu_application_key`(`application_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1245 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '菜单权限统一表' ROW_FORMAT = DYNAMIC;

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
) ENGINE = InnoDB AUTO_INCREMENT = 250 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通知公告表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_password_reset_token
-- ----------------------------
DROP TABLE IF EXISTS `sys_password_reset_token`;
CREATE TABLE `sys_password_reset_token`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `token_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '重置令牌哈希',
  `expires_at` timestamp NOT NULL COMMENT '过期时间',
  `used_at` timestamp NULL DEFAULT NULL COMMENT '使用时间',
  `revoked_at` timestamp NULL DEFAULT NULL COMMENT '废弃时间',
  `request_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '请求IP',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_password_reset_token_hash`(`token_hash` ASC) USING BTREE,
  INDEX `idx_sys_password_reset_token_user`(`tenant_id` ASC, `user_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_sys_password_reset_token_username`(`tenant_id` ASC, `username` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_sys_password_reset_token_ip`(`request_ip` ASC, `created_at` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 67 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '密码重置令牌表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板所属租户，固定为 platform',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '父权限 ID',
  `ancestors` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '祖先权限 ID 列表，逗号分隔',
  `perm_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限类型：BUTTON/API',
  `perm_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限唯一标识',
  `perm_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `grant_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '授权键',
  `order_no` int NOT NULL DEFAULT 0 COMMENT '排序值',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用 (0禁用 1启用)',
  `is_system` tinyint NOT NULL DEFAULT 0 COMMENT '是否系统内置',
  `module_tag` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '模块标识',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_permission_tenant_code`(`tenant_id` ASC, `perm_code` ASC) USING BTREE,
  UNIQUE INDEX `uk_sys_permission_tenant_grant`(`tenant_id` ASC, `grant_key` ASC) USING BTREE,
  INDEX `idx_sys_permission_tenant_parent`(`tenant_id` ASC, `parent_id` ASC) USING BTREE,
  INDEX `idx_sys_permission_tenant_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1003 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '权限模板表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_platform_security_policy
-- ----------------------------
DROP TABLE IF EXISTS `sys_platform_security_policy`;
CREATE TABLE `sys_platform_security_policy`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `password_min_length` int NOT NULL DEFAULT 8 COMMENT '密码最小长度',
  `password_max_length` int NOT NULL DEFAULT 64 COMMENT '密码最大长度',
  `password_require_letter` tinyint NOT NULL DEFAULT 1 COMMENT '密码要求字母',
  `password_require_number` tinyint NOT NULL DEFAULT 1 COMMENT '密码要求数字',
  `password_require_special` tinyint NOT NULL DEFAULT 0 COMMENT '密码要求特殊字符',
  `password_history_count` int NOT NULL DEFAULT 0 COMMENT '密码历史校验数量',
  `password_expire_days` int NOT NULL DEFAULT 90 COMMENT '密码过期天数，0 表示不过期',
  `login_failure_max_attempts` int NOT NULL DEFAULT 5 COMMENT '登录失败锁定阈值',
  `login_failure_lock_minutes` int NOT NULL DEFAULT 15 COMMENT '登录失败锁定分钟数',
  `login_failure_window_minutes` int NOT NULL DEFAULT 15 COMMENT '登录失败统计窗口分钟数',
  `captcha_enabled` tinyint NOT NULL DEFAULT 1 COMMENT '验证码开关',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_platform_security_policy_deleted`(`deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台默认安全策略表' ROW_FORMAT = DYNAMIC;

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
) ENGINE = InnoDB AUTO_INCREMENT = 2546 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `menu_id` bigint NOT NULL COMMENT '菜单/权限节点 ID',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_role_menu_tenant_role`(`tenant_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_sys_role_menu_tenant_menu`(`tenant_id` ASC, `menu_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6080 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色菜单权限关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `permission_id` bigint NOT NULL COMMENT '权限 ID',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_role_permission_tenant_role_perm`(`tenant_id` ASC, `role_id` ASC, `permission_id` ASC) USING BTREE,
  INDEX `idx_sys_role_permission_tenant_role`(`tenant_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_sys_role_permission_tenant_perm`(`tenant_id` ASC, `permission_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5003 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色权限关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_storage_file
-- ----------------------------
DROP TABLE IF EXISTS `sys_storage_file`;
CREATE TABLE `sys_storage_file`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `file_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件唯一键',
  `original_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `content_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容类型',
  `file_size` bigint NOT NULL COMMENT '文件大小，单位字节',
  `storage_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储类型：MINIO',
  `bucket_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储桶名称',
  `object_key` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对象存储键',
  `etag` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '对象 ETag',
  `visibility` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '可见性：PUBLIC/TENANT/OWNER/PRIVATE',
  `owner_user_id` bigint NULL DEFAULT NULL COMMENT '所有者用户 ID',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_storage_file_key`(`file_key` ASC) USING BTREE,
  INDEX `idx_sys_storage_file_tenant_visibility`(`tenant_id` ASC, `visibility` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_sys_storage_file_tenant_owner`(`tenant_id` ASC, `owner_user_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_sys_storage_file_created_at`(`created_at` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 228 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件存储记录表' ROW_FORMAT = DYNAMIC;

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
  `auth_begin_at` datetime NULL DEFAULT NULL COMMENT '授权开始时间',
  `expire_at` datetime NULL DEFAULT NULL COMMENT '租户到期时间',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '套餐编码',
  `logo_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '租户 Logo 地址',
  `contact_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人电话',
  `contact_email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人邮箱',
  `website` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '官网地址',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系地址',
  `lifecycle_note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '运营备注',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `tenant_id`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_sys_tenant_status`(`tenant_status` ASC) USING BTREE,
  INDEX `idx_sys_tenant_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_sys_tenant_auth_window`(`tenant_id` ASC, `deleted` ASC, `tenant_status` ASC, `auth_begin_at` ASC, `expire_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 37 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户主表' ROW_FORMAT = DYNAMIC;

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
) ENGINE = InnoDB AUTO_INCREMENT = 97 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户能力定义表' ROW_FORMAT = DYNAMIC;

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
) ENGINE = InnoDB AUTO_INCREMENT = 202 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户能力覆盖表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_tenant_capability_resource_scope
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_capability_resource_scope`;
CREATE TABLE `sys_tenant_capability_resource_scope`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'platform' COMMENT '所属租户，当前固定为 platform',
  `capability_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力编码',
  `resource_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源键',
  `scope_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'VISIBLE' COMMENT '范围类型：VISIBLE 控制菜单可见，GRANT 控制授权建议',
  `required` tinyint NOT NULL DEFAULT 0 COMMENT '是否为能力基础资源',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'system' COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'system' COMMENT '更新人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_capability_resource_scope`(`tenant_id` ASC, `capability_code` ASC, `resource_key` ASC, `scope_type` ASC) USING BTREE,
  INDEX `idx_capability_resource_scope_resource`(`tenant_id` ASC, `resource_key` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 343 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户能力资源范围表' ROW_FORMAT = DYNAMIC;

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
) ENGINE = InnoDB AUTO_INCREMENT = 1478 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户变更记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_tenant_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_menu`;
CREATE TABLE `sys_tenant_menu`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_menu_tenant`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_tenant_menu_menu`(`menu_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户分配菜单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_tenant_package
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_package`;
CREATE TABLE `sys_tenant_package`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户，当前固定为 platform',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐编码',
  `package_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '套餐名称',
  `subtitle` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '运营副标题',
  `sales_price` decimal(12, 2) NULL DEFAULT NULL COMMENT '销售价',
  `original_price` decimal(12, 2) NULL DEFAULT NULL COMMENT '原价',
  `description_md` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '富文本或 Markdown 描述',
  `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '应用标识',
  `order_no` int NOT NULL DEFAULT 0 COMMENT '展示排序',
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
  UNIQUE INDEX `uk_sys_tenant_package_tenant_code`(`tenant_id` ASC, `package_code` ASC) USING BTREE,
  INDEX `idx_sys_tenant_package_tenant_enabled_order`(`tenant_id` ASC, `deleted` ASC, `enabled` ASC, `order_no` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 119 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户套餐定义表' ROW_FORMAT = DYNAMIC;

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
) ENGINE = InnoDB AUTO_INCREMENT = 258 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户套餐能力关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_tenant_security_policy
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant_security_policy`;
CREATE TABLE `sys_tenant_security_policy`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `password_min_length` int NULL DEFAULT NULL COMMENT '密码最小长度，空值继承平台默认值',
  `password_max_length` int NULL DEFAULT NULL COMMENT '密码最大长度，空值继承平台默认值',
  `password_require_letter` tinyint NULL DEFAULT NULL COMMENT '密码要求字母，空值继承平台默认值',
  `password_require_number` tinyint NULL DEFAULT NULL COMMENT '密码要求数字，空值继承平台默认值',
  `password_require_special` tinyint NULL DEFAULT NULL COMMENT '密码要求特殊字符，空值继承平台默认值',
  `password_history_count` int NULL DEFAULT NULL COMMENT '密码历史校验数量，空值继承平台默认值',
  `password_expire_days` int NULL DEFAULT NULL COMMENT '密码过期天数，空值继承平台默认值',
  `login_failure_max_attempts` int NULL DEFAULT NULL COMMENT '登录失败锁定阈值，空值继承平台默认值',
  `login_failure_lock_minutes` int NULL DEFAULT NULL COMMENT '登录失败锁定分钟数，空值继承平台默认值',
  `login_failure_window_minutes` int NULL DEFAULT NULL COMMENT '登录失败统计窗口分钟数，空值继承平台默认值',
  `captcha_enabled` tinyint NULL DEFAULT NULL COMMENT '验证码开关，空值继承平台默认值',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_tenant_security_policy_tenant`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 29 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户安全策略覆盖表' ROW_FORMAT = DYNAMIC;

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
  `must_change_password` tinyint NOT NULL DEFAULT 0 COMMENT '是否必须修改密码',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `display_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户显示名称',
  `mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `avatar_file_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '头像文件键',
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
) ENGINE = InnoDB AUTO_INCREMENT = 10422 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户账号表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for sys_user_notification
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_notification`;
CREATE TABLE `sys_user_notification`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `recipient_user_id` bigint NOT NULL COMMENT '接收用户 ID',
  `scenario_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '通知场景编码',
  `source_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源类型',
  `source_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源对象 ID',
  `biz_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '业务类型',
  `biz_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '业务 ID',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '通知内容',
  `level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INFO' COMMENT '通知级别',
  `link` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '站内跳转链接',
  `action_payload_json` json NULL COMMENT '动作参数 JSON',
  `metadata_json` json NULL COMMENT '扩展元数据 JSON',
  `dedup_key` varchar(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '去重键',
  `read_at` timestamp NULL DEFAULT NULL COMMENT '已读时间',
  `expires_at` timestamp NULL DEFAULT NULL COMMENT '过期时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_user_notification_dedup`(`tenant_id` ASC, `recipient_user_id` ASC, `dedup_key` ASC) USING BTREE,
  INDEX `idx_sys_user_notification_user_created`(`tenant_id` ASC, `recipient_user_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_sys_user_notification_user_read`(`tenant_id` ASC, `recipient_user_id` ASC, `read_at` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_sys_user_notification_source`(`tenant_id` ASC, `source_type` ASC, `source_id` ASC) USING BTREE,
  INDEX `idx_sys_user_notification_biz`(`tenant_id` ASC, `biz_type` ASC, `biz_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1425 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户站内通知表' ROW_FORMAT = DYNAMIC;

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
) ENGINE = InnoDB AUTO_INCREMENT = 1711 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户角色关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for wf_process_definition
-- ----------------------------
DROP TABLE IF EXISTS `wf_process_definition`;
CREATE TABLE `wf_process_definition`  (
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
  UNIQUE INDEX `uk_wf_process_definition_tenant_key_version`(`tenant_id` ASC, `definition_key` ASC, `version` ASC) USING BTREE,
  INDEX `idx_wf_process_definition_tenant_status`(`tenant_id` ASC, `status` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 215 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流流程定义表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for wf_process_instance
-- ----------------------------
DROP TABLE IF EXISTS `wf_process_instance`;
CREATE TABLE `wf_process_instance`  (
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
  UNIQUE INDEX `uk_wf_process_instance_tenant_business`(`tenant_id` ASC, `business_key` ASC) USING BTREE,
  INDEX `idx_wf_process_instance_tenant_starter`(`tenant_id` ASC, `starter_user_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_wf_process_instance_tenant_definition`(`tenant_id` ASC, `definition_key` ASC, `definition_version` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 215 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流流程实例表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for wf_task
-- ----------------------------
DROP TABLE IF EXISTS `wf_task`;
CREATE TABLE `wf_task`  (
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
  INDEX `idx_wf_task_tenant_instance`(`tenant_id` ASC, `instance_id` ASC, `step_index` ASC) USING BTREE,
  INDEX `idx_wf_task_tenant_status_assignee`(`tenant_id` ASC, `status` ASC, `assignee_user_id` ASC) USING BTREE,
  INDEX `idx_wf_task_tenant_status_created`(`tenant_id` ASC, `status` ASC, `created_at` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 349 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流任务表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for wf_task_urge
-- ----------------------------
DROP TABLE IF EXISTS `wf_task_urge`;
CREATE TABLE `wf_task_urge`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `task_id` bigint NOT NULL COMMENT '任务 ID',
  `instance_id` bigint NOT NULL COMMENT '流程实例 ID',
  `urged_by_user_id` bigint NOT NULL COMMENT '催办人用户 ID',
  `urged_by_username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '催办人用户名',
  `comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '催办说明',
  `urged_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '催办时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_wf_task_urge_tenant_task`(`tenant_id` ASC, `task_id` ASC, `urged_at` DESC) USING BTREE,
  INDEX `idx_wf_task_urge_tenant_instance`(`tenant_id` ASC, `instance_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流任务催办记录表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
