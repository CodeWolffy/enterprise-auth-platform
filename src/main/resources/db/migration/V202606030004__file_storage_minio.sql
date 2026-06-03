CREATE TABLE IF NOT EXISTS `sys_storage_file` (
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
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件存储记录表' ROW_FORMAT = DYNAMIC;

ALTER TABLE `sys_user`
  ADD COLUMN `avatar_file_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '头像文件键' AFTER `email`;

INSERT INTO `sys_resource` (
  `tenant_id`,
  `parent_id`,
  `ancestors`,
  `resource_type`,
  `resource_key`,
  `resource_name`,
  `route_key`,
  `grant_key`,
  `path`,
  `component`,
  `icon`,
  `order_no`,
  `visible`,
  `enabled`,
  `is_system`,
  `created_by`,
  `updated_by`,
  `deleted`,
  `created_at`,
  `updated_at`
)
SELECT
  'platform',
  parent.`id`,
  CONCAT('1,', parent.`id`),
  'MENU',
  'files',
  '文件管理',
  'files',
  'file:read',
  '/platform/files',
  'FileManagementView',
  'FolderOpened',
  70,
  1,
  1,
  1,
  'system',
  'system',
  0,
  NOW(),
  NOW()
FROM `sys_resource` parent
WHERE parent.`tenant_id` = 'platform'
  AND parent.`resource_key` = 'platform-management'
  AND parent.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `ancestors` = VALUES(`ancestors`),
  `resource_type` = VALUES(`resource_type`),
  `resource_name` = VALUES(`resource_name`),
  `route_key` = VALUES(`route_key`),
  `grant_key` = VALUES(`grant_key`),
  `path` = VALUES(`path`),
  `component` = VALUES(`component`),
  `icon` = VALUES(`icon`),
  `order_no` = VALUES(`order_no`),
  `visible` = VALUES(`visible`),
  `enabled` = VALUES(`enabled`),
  `is_system` = VALUES(`is_system`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = VALUES(`deleted`),
  `updated_at` = NOW();

INSERT INTO `sys_resource` (
  `tenant_id`,
  `parent_id`,
  `ancestors`,
  `resource_type`,
  `resource_key`,
  `resource_name`,
  `route_key`,
  `grant_key`,
  `path`,
  `component`,
  `icon`,
  `order_no`,
  `visible`,
  `enabled`,
  `is_system`,
  `created_by`,
  `updated_by`,
  `deleted`,
  `created_at`,
  `updated_at`
)
SELECT
  'platform',
  parent.`id`,
  CONCAT('1,', parent.`id`),
  'API',
  'api.file.read',
  '文件读',
  NULL,
  'file:read',
  NULL,
  NULL,
  NULL,
  160,
  0,
  1,
  1,
  'system',
  'system',
  0,
  NOW(),
  NOW()
FROM `sys_resource` parent
WHERE parent.`tenant_id` = 'platform'
  AND parent.`resource_key` = 'api'
  AND parent.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `ancestors` = VALUES(`ancestors`),
  `resource_type` = VALUES(`resource_type`),
  `resource_name` = VALUES(`resource_name`),
  `grant_key` = VALUES(`grant_key`),
  `order_no` = VALUES(`order_no`),
  `visible` = VALUES(`visible`),
  `enabled` = VALUES(`enabled`),
  `is_system` = VALUES(`is_system`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = VALUES(`deleted`),
  `updated_at` = NOW();

INSERT INTO `sys_resource` (
  `tenant_id`,
  `parent_id`,
  `ancestors`,
  `resource_type`,
  `resource_key`,
  `resource_name`,
  `route_key`,
  `grant_key`,
  `path`,
  `component`,
  `icon`,
  `order_no`,
  `visible`,
  `enabled`,
  `is_system`,
  `created_by`,
  `updated_by`,
  `deleted`,
  `created_at`,
  `updated_at`
)
SELECT
  'platform',
  parent.`id`,
  CONCAT('1,', parent.`id`),
  'API',
  'api.file.write',
  '文件写',
  NULL,
  'file:write',
  NULL,
  NULL,
  NULL,
  170,
  0,
  1,
  1,
  'system',
  'system',
  0,
  NOW(),
  NOW()
FROM `sys_resource` parent
WHERE parent.`tenant_id` = 'platform'
  AND parent.`resource_key` = 'api'
  AND parent.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`),
  `ancestors` = VALUES(`ancestors`),
  `resource_type` = VALUES(`resource_type`),
  `resource_name` = VALUES(`resource_name`),
  `grant_key` = VALUES(`grant_key`),
  `order_no` = VALUES(`order_no`),
  `visible` = VALUES(`visible`),
  `enabled` = VALUES(`enabled`),
  `is_system` = VALUES(`is_system`),
  `updated_by` = VALUES(`updated_by`),
  `deleted` = VALUES(`deleted`),
  `updated_at` = NOW();

INSERT INTO `sys_role_resource` (
  `tenant_id`,
  `role_id`,
  `resource_id`,
  `created_by`,
  `updated_by`,
  `created_at`,
  `updated_at`
)
SELECT
  'platform',
  role.`id`,
  resource.`id`,
  'system',
  'system',
  NOW(),
  NOW()
FROM `sys_role` role
JOIN `sys_resource` resource
  ON resource.`tenant_id` = 'platform'
  AND resource.`resource_key` IN ('files', 'api.file.read', 'api.file.write')
  AND resource.`deleted` = 0
WHERE role.`tenant_id` = 'platform'
  AND role.`role_code` = 'ADMIN'
  AND role.`deleted` = 0
ON DUPLICATE KEY UPDATE
  `updated_by` = VALUES(`updated_by`),
  `updated_at` = NOW();