-- =====================================================
-- 创建 sys_tenant_menu 表：对齐 haorong-mall 租户菜单分配模型
-- =====================================================

CREATE TABLE `sys_tenant_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) NOT NULL COMMENT '租户ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  `created_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_menu_tenant` (`tenant_id`),
  KEY `idx_tenant_menu_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='租户分配菜单表';