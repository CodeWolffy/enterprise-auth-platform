-- =====================================================
-- P1 部门运营字段补齐：负责人、电话、排序、启停状态
-- =====================================================

ALTER TABLE `sys_dept`
  ADD COLUMN `leader_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人姓名' AFTER `leader_user_id`,
  ADD COLUMN `leader_phone` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '负责人电话' AFTER `leader_name`,
  ADD COLUMN `order_no` int NOT NULL DEFAULT 0 COMMENT '排序序号' AFTER `leader_phone`,
  ADD COLUMN `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '启用状态：0 停用，1 启用' AFTER `order_no`;

CREATE INDEX `idx_sys_dept_tenant_status_order`
  ON `sys_dept` (`tenant_id`, `deleted`, `enabled`, `order_no`);