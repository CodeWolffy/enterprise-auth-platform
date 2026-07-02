-- ----------------------------------------------------------------------------
-- V3: 重整 sys_menu 以适配 Vben Admin v5 动态路由（菜单驱动）
--
-- Vben 路由解析约定（见 generate-routes-backend.ts / accessible.ts）：
--   * 叶子菜单 component = Vben 视图路径（无前导 '/'，无 '.vue'），
--     例如 'upms/role/index' -> apps/web-ele/src/views/upms/role/index.vue
--   * 目录（含子菜单）component 置空：Vben 会自动将其作为分组路由，
--     并由根布局 BasicLayout 统一包裹；目录必须有唯一且以 '/' 开头的 path，
--     Vben 会自动 redirect 到第一个子路由。
--   * 按钮（type='1'）不参与路由，保持 component=NULL。
--
-- 注意：仅重整「已存在对应 Vben 视图」的基座模块；其余自建模块
--      （系统设置/文件/参数/邮件/公告/分类/工作流等）暂保留旧 component，
--      其页面将在 Phase 3 迁移后再行对齐（此前点击会落到 not-found 兜底页）。
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

-- 1) 顶级目录：补充 path，并清空 component（作为分组路由）
UPDATE `sys_menu` SET `path` = '/system',   `component` = NULL WHERE `id` = 200; -- 系统管理
UPDATE `sys_menu` SET `path` = '/platform', `component` = NULL WHERE `id` = 300; -- 平台管理
UPDATE `sys_menu` SET `path` = '/workflow', `component` = NULL WHERE `id` = 400; -- 工作流

-- 2) 叶子菜单：component 指向已存在的 Vben 视图
UPDATE `sys_menu` SET `component` = 'dashboard/analytics/index'  WHERE `id` = 100; -- 运行总览
UPDATE `sys_menu` SET `component` = 'upms/user/index'           WHERE `id` = 210; -- 用户管理
UPDATE `sys_menu` SET `component` = 'upms/role/index'           WHERE `id` = 220; -- 角色管理
UPDATE `sys_menu` SET `component` = 'upms/dept/index'           WHERE `id` = 230; -- 部门管理
UPDATE `sys_menu` SET `component` = 'upms/online-user/index'    WHERE `id` = 240; -- 在线用户
UPDATE `sys_menu` SET `component` = 'upms/menu/index'           WHERE `id` = 250; -- 菜单管理
UPDATE `sys_menu` SET `component` = 'upms/log/index'            WHERE `id` = 270; -- 操作日志
UPDATE `sys_menu` SET `component` = 'upms/login-log/index'      WHERE `id` = 280; -- 登录日志
UPDATE `sys_menu` SET `component` = 'upms/tenant/index'         WHERE `id` = 310; -- 租户管理
UPDATE `sys_menu` SET `component` = 'upms/tenant-package/index' WHERE `id` = 320; -- 租户套餐
UPDATE `sys_menu` SET `component` = 'upms/dict/index'           WHERE `id` = 340; -- 字典管理
UPDATE `sys_menu` SET `component` = 'gen/gen-table/index'       WHERE `id` = 390; -- 代码生成
