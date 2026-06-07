-- =====================================================
-- 菜单权限统一表：参考 haorong-mall 的 sys_menu + sys_role_menu 设计
-- 目录、菜单、按钮权限、API 权限统一由 sys_menu 承载
-- 角色授权统一由 sys_role_menu 承载
-- =====================================================

CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板所属租户，固定为 platform',
  `parent_id` bigint NULL DEFAULT NULL COMMENT '父节点 ID',
  `ancestors` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '祖先节点 ID 列表，逗号分隔',
  `menu_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点类型：DIR/MENU/BUTTON/API',
  `resource_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '资源唯一标识',
  `menu_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点名称',
  `route_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前端路由标识',
  `grant_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '统一授权键',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前端路由路径',
  `component` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '前端组件名称',
  `redirect` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '重定向路径',
  `icon` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '图标标识',
  `order_no` int NOT NULL DEFAULT 0 COMMENT '显示排序',
  `visible` tinyint NOT NULL DEFAULT 1 COMMENT '菜单树中是否可见 (0隐藏 1可见)',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用 (0禁用 1启用)',
  `is_system` tinyint NOT NULL DEFAULT 0 COMMENT '是否系统内置 (1内置 0非内置)',
  `outer_status` tinyint NOT NULL DEFAULT 0 COMMENT '外链状态 (0否 1是)',
  `application_key` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '应用标识，多应用隔离',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 (0未删除 1已删除)',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_menu_tenant_resource_key`(`tenant_id` ASC, `resource_key` ASC) USING BTREE,
  UNIQUE INDEX `uk_sys_menu_tenant_route`(`tenant_id` ASC, `route_key` ASC) USING BTREE,
  UNIQUE INDEX `uk_sys_menu_tenant_path`(`tenant_id` ASC, `path` ASC) USING BTREE,
  INDEX `idx_sys_menu_tenant_parent`(`tenant_id` ASC, `parent_id` ASC) USING BTREE,
  INDEX `idx_sys_menu_tenant_type`(`tenant_id` ASC, `menu_type` ASC) USING BTREE,
  INDEX `idx_sys_menu_tenant_grant`(`tenant_id` ASC, `grant_key` ASC) USING BTREE,
  INDEX `idx_sys_menu_tenant_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1000 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '菜单权限统一表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `menu_id` bigint NOT NULL COMMENT '菜单/权限节点 ID',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_role_menu_tenant_role_menu`(`tenant_id` ASC, `role_id` ASC, `menu_id` ASC) USING BTREE,
  INDEX `idx_sys_role_menu_tenant_role`(`tenant_id` ASC, `role_id` ASC) USING BTREE,
  INDEX `idx_sys_role_menu_tenant_menu`(`tenant_id` ASC, `menu_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5000 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '角色菜单权限关联表' ROW_FORMAT = DYNAMIC;