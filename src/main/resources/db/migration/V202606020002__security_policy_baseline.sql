-- P0-A security policy baseline.
-- Rollback: DROP TABLE sys_tenant_security_policy; DROP TABLE sys_platform_security_policy;

CREATE TABLE IF NOT EXISTS `sys_platform_security_policy` (
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
  INDEX `idx_sys_platform_security_policy_deleted` (`deleted` ASC) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台默认安全策略表' ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS `sys_tenant_security_policy` (
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
  UNIQUE INDEX `uk_sys_tenant_security_policy_tenant` (`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户安全策略覆盖表' ROW_FORMAT = DYNAMIC;

INSERT INTO `sys_platform_security_policy` (
  `password_min_length`, `password_max_length`, `password_require_letter`, `password_require_number`,
  `password_require_special`, `password_history_count`, `password_expire_days`, `login_failure_max_attempts`,
  `login_failure_lock_minutes`, `login_failure_window_minutes`, `captcha_enabled`, `created_by`, `updated_by`, `deleted`
)
SELECT 8, 64, 1, 1, 0, 0, 90, 5, 15, 15, 1, 'system', 'system', 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_platform_security_policy` WHERE `deleted` = 0);

INSERT INTO `sys_tenant_security_policy` (`tenant_id`, `created_by`, `updated_by`, `deleted`)
SELECT t.`tenant_id`, 'system', 'system', 0
FROM `sys_tenant` t
WHERE t.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_tenant_security_policy` p WHERE p.`tenant_id` = t.`tenant_id` AND p.`deleted` = 0
  );