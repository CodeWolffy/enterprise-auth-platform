-- ----------------------------------------------------------------------------
-- V10: 将菜单图标统一修正为 Vben/Iconify 可直接渲染的 Carbon 图标名
--
-- 约束：sys_menu.icon 是菜单图标的唯一来源，前端不再维护 Element Plus -> Iconify
-- 兼容映射。这里直接把数据库值改成 Iconify 标准格式。
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

UPDATE `sys_menu`
SET `icon` = CASE `id`
  WHEN 100 THEN 'carbon:dashboard'          -- 运行总览
  WHEN 200 THEN 'carbon:settings'           -- 系统管理
  WHEN 300 THEN 'carbon:platforms'          -- 平台管理
  WHEN 400 THEN 'carbon:flow'               -- 工作流
  WHEN 210 THEN 'carbon:user-avatar'        -- 用户管理
  WHEN 220 THEN 'carbon:user-role'          -- 角色管理
  WHEN 230 THEN 'carbon:tree-view'          -- 部门管理
  WHEN 240 THEN 'carbon:user-online'       -- 在线用户
  WHEN 250 THEN 'carbon:menu'               -- 菜单管理
  WHEN 260 THEN 'carbon:security'           -- 系统设置
  WHEN 270 THEN 'carbon:document'           -- 操作日志
  WHEN 280 THEN 'carbon:login'              -- 登录日志
  WHEN 310 THEN 'carbon:building'           -- 租户管理
  WHEN 320 THEN 'carbon:catalog'            -- 租户套餐
  WHEN 330 THEN 'carbon:folder-open'        -- 文件管理
  WHEN 340 THEN 'carbon:data-table'         -- 字典管理
  WHEN 350 THEN 'carbon:settings-adjust'    -- 参数管理
  WHEN 360 THEN 'carbon:email'              -- 邮件配置
  WHEN 370 THEN 'carbon:notification'       -- 公告管理
  WHEN 380 THEN 'carbon:category'           -- 分类配置
  WHEN 390 THEN 'carbon:code'               -- 代码生成
  WHEN 410 THEN 'carbon:flow-data'          -- 流程设计器
  WHEN 420 THEN 'carbon:flow-connection'    -- 流程定义
  WHEN 430 THEN 'carbon:document-signed'    -- 我的发起
  WHEN 440 THEN 'carbon:task'               -- 我的待办
  WHEN 450 THEN 'carbon:task-complete'      -- 我的已办
  ELSE `icon`
END
WHERE `deleted` = 0
  AND `id` IN (
    100, 200, 300, 400,
    210, 220, 230, 240, 250, 260, 270, 280,
    310, 320, 330, 340, 350, 360, 370, 380, 390,
    410, 420, 430, 440, 450
  );