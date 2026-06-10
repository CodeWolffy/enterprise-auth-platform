ALTER TABLE `codegen_template`
  ADD COLUMN `template_category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'backend' COMMENT '模板分类：backend/frontend/api/type/view' AFTER `language`;

UPDATE `codegen_template`
SET `template_category` = CASE
  WHEN `language` = 'vue' THEN 'view'
  WHEN `language` = 'typescript' AND (`path_pattern` LIKE '%api%' OR `path_pattern` LIKE '%Api%') THEN 'api'
  WHEN `language` = 'typescript' THEN 'type'
  ELSE 'backend'
END
WHERE `deleted` = 0;

CREATE INDEX `idx_codegen_template_tenant_category` ON `codegen_template` (`tenant_id`, `template_category`, `deleted`);