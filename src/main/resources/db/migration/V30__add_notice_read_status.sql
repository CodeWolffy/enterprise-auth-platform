CREATE TABLE IF NOT EXISTS `sys_notice_read_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `tenant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属租户',
  `notice_id` bigint NOT NULL COMMENT '公告 ID',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `read_at` datetime(3) NULL DEFAULT NULL COMMENT '已读时间',
  `cleared_at` datetime(3) NULL DEFAULT NULL COMMENT '清空时间',
  `created_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '创建人',
  `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sys_notice_read_status_user_notice`(`tenant_id` ASC, `user_id` ASC, `notice_id` ASC) USING BTREE,
  INDEX `idx_sys_notice_read_status_notice`(`tenant_id` ASC, `notice_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_sys_notice_read_status_user_read`(`tenant_id` ASC, `user_id` ASC, `read_at` ASC, `cleared_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '公告用户已读状态表' ROW_FORMAT = DYNAMIC;
