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
SET time_zone = '+00:00';
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
  `authorized_at` datetime(3) NULL DEFAULT NULL COMMENT '外部数据源授权时间',
  `authorization_note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '外部数据源授权说明',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_codegen_template_tenant_name`(`tenant_id` ASC, `name` ASC) USING BTREE,
  INDEX `idx_codegen_template_tenant_lang_pattern`(`tenant_id` ASC, `language` ASC, `path_pattern` ASC) USING BTREE,
  INDEX `idx_codegen_template_tenant_category`(`tenant_id` ASC, `template_category` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '代码生成自定义模板' ROW_FORMAT = DYNAMIC;


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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `config_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置名称',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `dept_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门编码',
  `leader_user_id` bigint NULL DEFAULT NULL COMMENT '部门负责人用户 ID',
  `leader_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人姓名',
  `leader_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人电话',
  `order_no` int NOT NULL DEFAULT 0 COMMENT '排序序号',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态：0 停用，1 启用',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `create_time` datetime(3) NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` datetime(3) NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `publish_time` datetime(3) NULL DEFAULT NULL COMMENT '发布时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `expires_at` datetime(3) NOT NULL COMMENT '过期时间',
  `used_at` datetime(3) NULL DEFAULT NULL COMMENT '使用时间',
  `revoked_at` datetime(3) NULL DEFAULT NULL COMMENT '废弃时间',
  `request_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '请求IP',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_password_reset_token_hash`(`token_hash` ASC) USING BTREE,
  INDEX `idx_sys_password_reset_token_user`(`tenant_id` ASC, `user_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_sys_password_reset_token_username`(`tenant_id` ASC, `username` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_sys_password_reset_token_ip`(`request_ip` ASC, `created_at` DESC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 67 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '密码重置令牌表' ROW_FORMAT = DYNAMIC;


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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `role_desc` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '角色描述',
  `data_scope_value_json` json NULL COMMENT '数据范围附加值 JSON',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `create_time` datetime(3) NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sys_role_menu_tenant_role`(`tenant_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_sys_role_menu_tenant_menu`(`tenant_id` ASC, `menu_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6080 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色菜单权限关联表' ROW_FORMAT = DYNAMIC;


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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `tenant_status` tinyint NOT NULL DEFAULT 1 COMMENT '租户状态：1 启用，0 禁用',
  `auth_begin_at` datetime(3) NULL DEFAULT NULL COMMENT '授权开始时间',
  `expire_at` datetime(3) NULL DEFAULT NULL COMMENT '租户到期时间',
  `package_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '套餐编码',
  `logo_url` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '租户 Logo 地址',
  `contact_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人电话',
  `contact_email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系人邮箱',
  `website` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '官网地址',
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '联系地址',
  `lifecycle_note` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '运营备注',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `occurred_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '变更时间',
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
  `create_time` datetime(3) NULL DEFAULT NULL COMMENT '创建时间',
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
  `package_desc` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '套餐说明',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0' COMMENT '状态：0 正常，1 停用',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_tenant_package_tenant_code`(`tenant_id` ASC, `package_code` ASC) USING BTREE,
  INDEX `idx_sys_tenant_package_tenant_status_order`(`tenant_id` ASC, `deleted` ASC, `status` ASC, `order_no` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 119 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户套餐定义表' ROW_FORMAT = DYNAMIC;


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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `display_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户显示名称',
  `mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `avatar_file_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '头像文件键',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0 未删除，1 已删除',
  `last_login_at` datetime(3) NULL DEFAULT NULL COMMENT '最近登录时间',
  `last_login_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '最近登录 IP',
  `password_updated_at` datetime(3) NULL DEFAULT NULL COMMENT '密码最近修改时间',
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
  `read_at` datetime(3) NULL DEFAULT NULL COMMENT '已读时间',
  `expires_at` datetime(3) NULL DEFAULT NULL COMMENT '过期时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发起时间',
  `ended_at` datetime(3) NULL DEFAULT NULL COMMENT '结束时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `completed_at` datetime(3) NULL DEFAULT NULL COMMENT '完成时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
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
  `urged_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '催办时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_wf_task_urge_tenant_task`(`tenant_id` ASC, `task_id` ASC, `urged_at` DESC) USING BTREE,
  INDEX `idx_wf_task_urge_tenant_instance`(`tenant_id` ASC, `instance_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 20 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作流任务催办记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Baseline seed data
-- Default development account: admin / Admin@123456
-- ----------------------------
INSERT INTO `sys_tenant` (`id`, `tenant_id`, `tenant_name`, `platform_level`, `tenant_status`, `auth_begin_at`, `expire_at`, `package_code`, `contact_name`, `contact_phone`, `contact_email`, `lifecycle_note`, `created_by`, `updated_by`, `deleted`) VALUES
(1, 'platform', '平台租户', 1, 1, UTC_TIMESTAMP(3), '2099-12-31 23:59:59', 'DEFAULT', '平台管理员', '13800000000', 'admin@example.com', '系统内置平台租户', 'system', 'system', 0);

INSERT INTO `sys_dept` (`id`, `tenant_id`, `parent_id`, `dept_name`, `dept_code`, `leader_name`, `leader_phone`, `order_no`, `enabled`, `created_by`, `updated_by`, `deleted`) VALUES
(1, 'platform', NULL, '平台总部', 'ROOT', '平台管理员', '13800000000', 0, 1, 'system', 'system', 0);

INSERT INTO `sys_role` (`id`, `tenant_id`, `role_code`, `role_name`, `data_scope_type`, `role_desc`, `data_scope_value_json`, `created_by`, `updated_by`, `deleted`) VALUES
(1, 'platform', 'ADMIN', '平台超级管理员', 'ALL', '平台内置超级管理员角色', NULL, 'system', 'system', 0),
(2, 'platform', 'TENANT_ADMIN', '租户管理员', 'ALL', '租户初始化管理员角色模板', NULL, 'system', 'system', 0),
(3, 'platform', 'USER', '普通用户', 'SELF', '基础普通用户角色', NULL, 'system', 'system', 0);

INSERT INTO `sys_user` (`id`, `tenant_id`, `dept_id`, `username`, `password_hash`, `enabled`, `session_version`, `must_change_password`, `display_name`, `mobile`, `email`, `created_by`, `updated_by`, `deleted`, `password_updated_at`) VALUES
(1, 'platform', 1, 'admin', '$2b$12$JreITuCCL.pquUQc1Xxve.GoIyhIh7.iyLu776/Mk4qkRY12YD206', 1, 1, 0, '平台管理员', '13800000000', 'admin@example.com', 'system', 'system', 0, UTC_TIMESTAMP(3));

INSERT INTO `sys_user_role` (`id`, `tenant_id`, `user_id`, `role_id`, `created_by`, `updated_by`) VALUES
(1, 'platform', 1, 1, 'system', 'system');

INSERT INTO `sys_platform_security_policy` (`id`, `password_min_length`, `password_max_length`, `password_require_letter`, `password_require_number`, `password_require_special`, `password_history_count`, `password_expire_days`, `login_failure_max_attempts`, `login_failure_lock_minutes`, `login_failure_window_minutes`, `captcha_enabled`, `created_by`, `updated_by`, `deleted`) VALUES
(1, 8, 64, 1, 1, 0, 0, 90, 5, 15, 15, 1, 'system', 'system', 0);

INSERT INTO `sys_tenant_package` (`id`, `tenant_id`, `package_code`, `package_name`, `subtitle`, `sales_price`, `original_price`, `description_md`, `app_key`, `order_no`, `package_desc`, `status`, `created_by`, `updated_by`, `deleted`) VALUES
(1, 'platform', 'DEFAULT', '默认全量套餐', '开发期默认套餐', 0.00, 0.00, '包含基础底座、平台管理、工作流和开发工具菜单。', 'base,system,platform,workflow,dev', 0, '系统默认全量菜单范围', '0', 'system', 'system', 0),
(2, 'platform', 'BASIC', '基础底座套餐', '标准租户基础能力', 0.00, 0.00, '包含用户、角色、部门、字典、参数等基础底座菜单。', 'base,system', 10, '基础租户菜单范围', '0', 'system', 'system', 0);

INSERT INTO `sys_dict` (`id`, `tenant_id`, `dict_type`, `dict_code`, `dict_value`, `description`, `enabled`, `remarks`, `created_by`, `updated_by`, `deleted`) VALUES
(1, 'platform', 'sys_status', 'sys_status', '状态', '通用启停状态', 1, '平台全局字典', 'system', 'system', 0),
(2, 'platform', 'menu_type', 'menu_type', '菜单类型', '菜单和按钮类型', 1, '平台全局字典', 'system', 'system', 0),
(3, 'platform', 'tenant_status', 'tenant_status', '租户状态', '租户启停状态', 1, '平台全局字典', 'system', 'system', 0),
(4, 'platform', 'tenant_package_app_key', 'tenant_package_app_key', '应用标识', '租户套餐应用标识', 1, '平台全局字典', 'system', 'system', 0);

INSERT INTO `sys_dict_value` (`id`, `tenant_id`, `dict_id`, `dict_type`, `dict_label`, `dict_value`, `show_class`, `sort`, `enabled`, `remarks`, `created_by`, `updated_by`, `deleted`) VALUES
(1, 'platform', 1, 'sys_status', '正常', '1', 'success', 1, 1, NULL, 'system', 'system', 0),
(2, 'platform', 1, 'sys_status', '停用', '0', 'info', 2, 1, NULL, 'system', 'system', 0),
(3, 'platform', 2, 'menu_type', '菜单', '0', 'primary', 1, 1, NULL, 'system', 'system', 0),
(4, 'platform', 2, 'menu_type', '按钮', '1', 'warning', 2, 1, NULL, 'system', 'system', 0),
(5, 'platform', 3, 'tenant_status', '正常', '1', 'success', 1, 1, NULL, 'system', 'system', 0),
(6, 'platform', 3, 'tenant_status', '停用', '0', 'danger', 2, 1, NULL, 'system', 'system', 0),
(7, 'platform', 4, 'tenant_package_app_key', '基础底座', 'base', 'primary', 1, 1, NULL, 'system', 'system', 0),
(8, 'platform', 4, 'tenant_package_app_key', '系统管理', 'system', 'primary', 2, 1, NULL, 'system', 'system', 0),
(9, 'platform', 4, 'tenant_package_app_key', '平台管理', 'platform', 'primary', 3, 1, NULL, 'system', 'system', 0),
(10, 'platform', 4, 'tenant_package_app_key', '工作流', 'workflow', 'primary', 4, 1, NULL, 'system', 'system', 0),
(11, 'platform', 4, 'tenant_package_app_key', '开发工具', 'dev', 'primary', 5, 1, NULL, 'system', 'system', 0);

INSERT INTO `sys_config` (`id`, `tenant_id`, `config_key`, `config_value`, `config_name`, `created_by`, `updated_by`, `deleted`) VALUES
(1, 'platform', 'system.name', 'Enterprise Auth Platform', '系统名称', 'system', 'system', 0),
(2, 'platform', 'registration.enabled', 'false', '是否开放注册', 'system', 'system', 0),
(3, 'platform', 'registration.default_tenant_id', 'platform', '注册默认租户', 'system', 'system', 0),
(4, 'platform', 'registration.default_role_codes', 'USER', '注册默认角色', 'system', 'system', 0);

INSERT INTO `sys_category_rule` (`id`, `tenant_id`, `target_type`, `category_code`, `category_name`, `matchers`, `created_by`, `updated_by`, `deleted`) VALUES
(1, 'platform', 'dict', 'system', '系统字典', 'sys_*,menu_*,tenant_*', 'system', 'system', 0),
(2, 'platform', 'config', 'registration', '注册参数', 'registration.*', 'system', 'system', 0);

INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `permission`, `path`, `component`, `sort`, `type`, `redirect`, `icon`, `outer_status`, `application_key`, `del_flag`, `create_by`, `update_by`, `deleted`) VALUES
(100, NULL, '运行总览', NULL, '/dashboard', 'dashboard/platform/index', 10, '0', NULL, 'carbon:dashboard', 0, 'base', '0', 'system', 'system', 0),
(101, 100, '运行总览列表', 'upms:dashboard:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'base', '0', 'system', 'system', 0),
(102, 100, '运行总览查询', 'upms:dashboard:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'base', '0', 'system', 'system', 0),
(200, NULL, '系统管理', NULL, '/system', NULL, 20, '0', NULL, 'carbon:settings', 0, 'system', '0', 'system', 'system', 0),
(210, 200, '用户管理', NULL, '/system/users', 'upms/user/index', 10, '0', NULL, 'carbon:user', 0, 'system', '0', 'system', 'system', 0),
(211, 210, '用户管理列表', 'upms:sysuser:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(212, 210, '用户管理查询', 'upms:sysuser:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(213, 210, '用户管理新增', 'upms:sysuser:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(214, 210, '用户管理修改', 'upms:sysuser:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(215, 210, '用户管理删除', 'upms:sysuser:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(220, 200, '角色管理', NULL, '/system/roles', 'upms/role/index', 20, '0', NULL, 'carbon:user-role', 0, 'system', '0', 'system', 'system', 0),
(221, 220, '角色管理列表', 'upms:sysrole:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(222, 220, '角色管理查询', 'upms:sysrole:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(223, 220, '角色管理新增', 'upms:sysrole:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(224, 220, '角色管理修改', 'upms:sysrole:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(225, 220, '角色管理删除', 'upms:sysrole:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(230, 200, '部门管理', NULL, '/system/depts', 'upms/dept/index', 30, '0', NULL, 'carbon:tree-view-alt', 0, 'system', '0', 'system', 'system', 0),
(231, 230, '部门管理列表', 'upms:sysdept:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(232, 230, '部门管理查询', 'upms:sysdept:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(233, 230, '部门管理新增', 'upms:sysdept:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(234, 230, '部门管理修改', 'upms:sysdept:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(235, 230, '部门管理删除', 'upms:sysdept:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(240, 200, '在线用户', NULL, '/system/online-users', 'upms/online-user/index', 40, '0', NULL, 'carbon:user-online', 0, 'system', '0', 'system', 'system', 0),
(241, 240, '在线用户列表', 'upms:session:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(242, 240, '在线用户查询', 'upms:session:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(243, 240, '在线用户强制下线', 'upms:session:kick', NULL, NULL, 3, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(250, 200, '菜单管理', NULL, '/system/menus', 'upms/menu/index', 50, '0', NULL, 'carbon:menu', 0, 'system', '0', 'system', 'system', 0),
(251, 250, '菜单管理列表', 'upms:sysmenu:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(252, 250, '菜单管理查询', 'upms:sysmenu:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(253, 250, '菜单管理新增', 'upms:sysmenu:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(254, 250, '菜单管理修改', 'upms:sysmenu:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(255, 250, '菜单管理删除', 'upms:sysmenu:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(260, 200, '系统设置', NULL, '/system/overview', 'system/SystemManagementView', 60, '0', NULL, 'carbon:settings', 0, 'system', '0', 'system', 'system', 0),
(261, 260, '系统设置列表', 'upms:system:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(262, 260, '系统设置查询', 'upms:system:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(263, 260, '安全策略查询', 'upms:security:get', NULL, NULL, 3, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(264, 260, '安全策略修改', 'upms:security:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(265, 200, '安全策略', NULL, '/system/security', 'system/SecurityPolicyView', 65, '0', NULL, 'carbon:security', 0, 'system', '0', 'system', 'system', 0),
(270, 200, '操作日志', NULL, '/system/logs/operation', 'upms/log/index', 70, '0', NULL, 'carbon:document', 0, 'system', '0', 'system', 'system', 0),
(271, 270, '操作日志列表', 'upms:operationlog:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(272, 270, '操作日志查询', 'upms:operationlog:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(280, 200, '登录日志', NULL, '/system/logs/login', 'upms/login-log/index', 80, '0', NULL, 'carbon:login', 0, 'system', '0', 'system', 'system', 0),
(281, 280, '登录日志列表', 'upms:loginlog:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(282, 280, '登录日志查询', 'upms:loginlog:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(300, NULL, '平台管理', NULL, '/platform', NULL, 30, '0', NULL, 'carbon:platforms', 0, 'platform', '0', 'system', 'system', 0),
(310, 300, '租户管理', NULL, '/platform/tenants', 'upms/tenant/index', 10, '0', NULL, 'carbon:enterprise', 0, 'platform', '0', 'system', 'system', 0),
(311, 310, '租户管理列表', 'upms:systenant:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(312, 310, '租户管理查询', 'upms:systenant:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(313, 310, '租户管理新增', 'upms:systenant:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(314, 310, '租户管理修改', 'upms:systenant:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(315, 310, '租户管理删除', 'upms:systenant:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(320, 300, '租户套餐', NULL, '/platform/tenant-catalog', 'system/tenant-catalog/index', 20, '0', NULL, 'carbon:catalog', 0, 'platform', '0', 'system', 'system', 0),
(321, 320, '租户套餐列表', 'upms:tenantpackage:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(322, 320, '租户套餐查询', 'upms:tenantpackage:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(323, 320, '租户套餐新增', 'upms:tenantpackage:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(324, 320, '租户套餐修改', 'upms:tenantpackage:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(325, 320, '租户套餐删除', 'upms:tenantpackage:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(330, 300, '文件管理', NULL, '/platform/files', 'upms/file/index', 30, '0', NULL, 'carbon:folder', 0, 'platform', '0', 'system', 'system', 0),
(331, 330, '文件管理列表', 'upms:file:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(332, 330, '文件管理查询', 'upms:file:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(333, 330, '文件管理上传', 'upms:file:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(334, 330, '文件管理删除', 'upms:file:del', NULL, NULL, 4, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(340, 300, '字典管理', NULL, '/platform/dicts', 'upms/dict/index', 40, '0', NULL, 'carbon:dictionary', 0, 'system', '0', 'system', 'system', 0),
(341, 340, '字典管理列表', 'upms:sysdict:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(342, 340, '字典管理查询', 'upms:sysdict:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(343, 340, '字典管理新增', 'upms:sysdict:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(344, 340, '字典管理修改', 'upms:sysdict:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(345, 340, '字典管理删除', 'upms:sysdict:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(350, 300, '参数管理', NULL, '/platform/configs', 'upms/config/index', 50, '0', NULL, 'carbon:settings-adjust', 0, 'system', '0', 'system', 'system', 0),
(351, 350, '系统参数列表', 'upms:sysconfig:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(352, 350, '系统参数查询', 'upms:sysconfig:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(353, 350, '系统参数新增', 'upms:sysconfig:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(354, 350, '系统参数修改', 'upms:sysconfig:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(355, 350, '系统参数删除', 'upms:sysconfig:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'system', '0', 'system', 'system', 0),
(360, 300, '邮件配置', NULL, '/platform/mail-channel', 'upms/mail-channel/index', 60, '0', NULL, 'carbon:email', 0, 'platform', '0', 'system', 'system', 0),
(361, 360, '邮件配置列表', 'upms:sysmail:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(362, 360, '邮件配置查询', 'upms:sysmail:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(363, 360, '邮件配置新增', 'upms:sysmail:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(364, 360, '邮件配置修改', 'upms:sysmail:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(365, 360, '邮件配置删除', 'upms:sysmail:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(370, 300, '公告管理', NULL, '/platform/notices', 'upms/notice/index', 70, '0', NULL, 'carbon:notification', 0, 'platform', '0', 'system', 'system', 0),
(371, 370, '公告管理列表', 'upms:sysnotice:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(372, 370, '公告管理查询', 'upms:sysnotice:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(373, 370, '公告管理新增', 'upms:sysnotice:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(374, 370, '公告管理修改', 'upms:sysnotice:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(375, 370, '公告管理删除', 'upms:sysnotice:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(380, 300, '分类配置', NULL, '/platform/categories', 'upms/category/index', 80, '0', NULL, 'carbon:category', 0, 'platform', '0', 'system', 'system', 0),
(381, 380, '分类配置列表', 'upms:syscategory:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(382, 380, '分类配置查询', 'upms:syscategory:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(383, 380, '分类配置新增', 'upms:syscategory:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(384, 380, '分类配置修改', 'upms:syscategory:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(385, 380, '分类配置删除', 'upms:syscategory:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'platform', '0', 'system', 'system', 0),
(390, 300, '代码生成', NULL, '/platform/codegen', NULL, 90, '0', '/platform/codegen/gen-table', 'carbon:ibm-cloud-code-engine', 0, 'dev', '0', 'system', 'system', 0),
(391, 390, '代码生成列表', 'upms:codegen:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(392, 390, '代码生成查询', 'upms:codegen:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(393, 390, '代码生成新增', 'upms:codegen:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(394, 390, '代码生成修改', 'upms:codegen:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(395, 390, '代码生成删除', 'upms:codegen:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(396, 390, '代码生成下载', 'upms:codegen:download', NULL, NULL, 6, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(397, 390, '数据源管理', NULL, '/platform/codegen/datasource', 'gen/datasource/index', 10, '0', NULL, 'carbon:database', 0, 'dev', '0', 'system', 'system', 0),
(398, 390, '数据表管理', NULL, '/platform/codegen/gen-table', 'gen/gen-table/index', 20, '0', NULL, 'carbon:data-table', 0, 'dev', '0', 'system', 'system', 0),
(399, 397, '数据源列表', 'gen:datasource:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(401, 397, '数据源新增', 'gen:datasource:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(402, 397, '数据源修改', 'gen:datasource:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(403, 397, '数据源删除', 'gen:datasource:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(404, 398, '数据表列表', 'gen:gen-table:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(405, 398, '数据表查询', 'gen:gen-table:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(406, 398, '数据表导入', 'gen:gen-table:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(407, 398, '数据表修改', 'gen:gen-table:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(408, 398, '代码生成下载', 'gen:gen-table:download', NULL, NULL, 5, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(409, 397, '数据源查询', 'gen:datasource:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(1341, 398, '数据表删除', 'gen:gen-table:del', NULL, NULL, 6, '1', NULL, NULL, 0, 'dev', '0', 'system', 'system', 0),
(400, NULL, '工作流', NULL, '/workflow', NULL, 40, '0', '/workflow/definitions', 'carbon:workflow-automation', 0, 'workflow', '0', 'system', 'system', 0),
(410, 400, '流程设计器', NULL, '/workflow/designer', 'workflow/designer', 10, '0', NULL, 'carbon:flow', 0, 'workflow', '0', 'system', 'system', 0),
(411, 410, '流程设计器入口', 'upms:workflowdesigner:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(420, 400, '流程定义', NULL, '/workflow/definitions', 'workflow/definitions', 20, '0', NULL, 'carbon:document', 0, 'workflow', '0', 'system', 'system', 0),
(421, 420, '流程定义列表', 'upms:workflowdefinition:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(422, 420, '流程定义查询', 'upms:workflowdefinition:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(423, 420, '流程定义新增', 'upms:workflowdefinition:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(424, 420, '流程定义修改', 'upms:workflowdefinition:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(425, 420, '流程定义部署', 'upms:workflowdefinition:deploy', NULL, NULL, 5, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(430, 400, '我的发起', NULL, '/workflow/instances', 'workflow/instances', 30, '0', NULL, 'carbon:send', 0, 'workflow', '0', 'system', 'system', 0),
(431, 430, '流程实例列表', 'upms:workflowinstance:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(432, 430, '流程实例查询', 'upms:workflowinstance:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(433, 430, '流程实例发起', 'upms:workflowinstance:add', NULL, NULL, 3, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(434, 430, '流程实例处理', 'upms:workflowinstance:edit', NULL, NULL, 4, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(435, 430, '流程实例终止', 'upms:workflowinstance:del', NULL, NULL, 5, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(440, 400, '我的待办', NULL, '/workflow/todo', 'workflow/todo', 40, '0', NULL, 'carbon:task', 0, 'workflow', '0', 'system', 'system', 0),
(441, 440, '我的待办列表', 'upms:workflowtodo:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(442, 440, '我的待办查询', 'upms:workflowtodo:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(443, 440, '我的待办处理', 'upms:workflowtodo:edit', NULL, NULL, 3, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(450, 400, '我的已办', NULL, '/workflow/done', 'workflow/done', 50, '0', NULL, 'carbon:task-complete', 0, 'workflow', '0', 'system', 'system', 0),
(451, 450, '我的已办列表', 'upms:workflowdone:page', NULL, NULL, 1, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0),
(452, 450, '我的已办查询', 'upms:workflowdone:get', NULL, NULL, 2, '1', NULL, NULL, 0, 'workflow', '0', 'system', 'system', 0);

INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`, `create_time`) SELECT 'platform', 1, `id`, UTC_TIMESTAMP(3) FROM `sys_menu` WHERE `del_flag` = '0';
INSERT INTO `sys_tenant_menu` (`tenant_id`, `menu_id`, `create_by`, `create_time`) SELECT 'platform', `id`, 'system', UTC_TIMESTAMP(3) FROM `sys_menu` WHERE `del_flag` = '0';

SET FOREIGN_KEY_CHECKS = 1;
