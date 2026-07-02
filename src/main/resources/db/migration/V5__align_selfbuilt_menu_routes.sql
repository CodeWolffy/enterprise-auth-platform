-- ----------------------------------------------------------------------------
-- V5: 自建模块菜单 component 对齐 Vben Admin v5 视图路径
--
-- V3 仅更新基座模块，自建模块保留旧 component（settings/files/mail-channel 等）。
-- 当前这些视图已新建，需统一改为 Vben 约定的相对路径（无前导 '/'，无 '.vue'）。
-- 工作流多页面暂共用 views/workflow/index.vue（内部 Tab 切换），后续再拆分。
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

-- 系统管理 - 自建模块
UPDATE `sys_menu` SET `component` = 'upms/security/index'   WHERE `id` = 260; -- 系统设置/安全策略
UPDATE `sys_menu` SET `component` = 'upms/file/index'        WHERE `id` = 330; -- 文件管理
UPDATE `sys_menu` SET `component` = 'upms/mail-channel/index' WHERE `id` = 360; -- 邮件配置
UPDATE `sys_menu` SET `component` = 'upms/notice/index'      WHERE `id` = 370; -- 公告管理
UPDATE `sys_menu` SET `component` = 'upms/category/index'    WHERE `id` = 380; -- 分类配置

-- 工作流（先统一指向 index，内部用 Tab 区分）
UPDATE `sys_menu` SET `component` = 'workflow/index' WHERE `id` IN (410, 420, 430, 440, 450);

-- 代码生成：V1 component='codegen'，当前前端视图为 gen/gen-table/index
UPDATE `sys_menu` SET `component` = 'gen/gen-table/index' WHERE `id` = 390;