-- ----------------------------------------------------------------------------
-- V6: 修复自建模块菜单 component 未对齐 Vben 视图路径的问题
--
-- V5 迁移文件已创建但未生效（flyway_schema_history 无 V5 记录），
-- 导致 sys_menu.component 仍为旧值（settings/files/mail-channel 等），
-- 前端 generate-routes-backend.ts 无法解析到对应 .vue 组件。
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

-- 系统管理 - 自建模块
UPDATE `sys_menu` SET `component` = 'upms/security/index'   WHERE `id` = 260; -- 系统设置/安全策略
UPDATE `sys_menu` SET `component` = 'upms/file/index'        WHERE `id` = 330; -- 文件管理
UPDATE `sys_menu` SET `component` = 'upms/mail-channel/index' WHERE `id` = 360; -- 邮件配置
UPDATE `sys_menu` SET `component` = 'upms/notice/index'      WHERE `id` = 370; -- 公告管理
UPDATE `sys_menu` SET `component` = 'upms/category/index'    WHERE `id` = 380; -- 分类配置

-- 工作流（统一指向 index，内部用 Tab 区分页面）
UPDATE `sys_menu` SET `component` = 'workflow/index' WHERE `id` IN (410, 420, 430, 440, 450);

-- 修复遗留的 Layout 组件值（id=1251 为平台管理下重复的代码生成入口）
UPDATE `sys_menu` SET `component` = 'gen/gen-table/index' WHERE `id` = 1251;