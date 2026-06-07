-- =====================================================
-- 字典二级模型增强：参考 haorong-mall 的 sys_dict_value 设计
-- 新增独立的字典值表，支持标签/键值/排序/回显样式
-- =====================================================

DROP TABLE IF EXISTS `sys_dict_value`;
CREATE TABLE `sys_dict_value` (
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
  INDEX `idx_sys_dict_value_tenant_type`(`tenant_id` ASC, `dict_type` ASC) USING BTREE,
  INDEX `idx_sys_dict_value_tenant_dict`(`tenant_id` ASC, `dict_id` ASC) USING BTREE,
  INDEX `idx_sys_dict_value_tenant_deleted`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 500 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '字典值表' ROW_FORMAT = DYNAMIC;

-- 从现有 sys_dict 数据迁移到 sys_dict_value（将 dict_type+dict_code+dict_value 映射为二级模型）
-- 先创建对应的字典类型记录，再把每条 dict 记录转为 dict_value
INSERT INTO `sys_dict_value` (`tenant_id`, `dict_id`, `dict_type`, `dict_label`, `dict_value`, `sort`, `enabled`, `created_by`, `created_at`)
SELECT
  d.`tenant_id`,
  d.`id`,
  d.`dict_type`,
  d.`dict_value` AS `dict_label`,
  d.`dict_code` AS `dict_value`,
  0,
  1,
  d.`created_by`,
  d.`created_at`
FROM `sys_dict` d
WHERE d.`deleted` = 0
  AND d.`dict_value` IS NOT NULL
  AND d.`dict_value` != '';