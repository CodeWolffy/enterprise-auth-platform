-- ----------------------------------------------------------------------------
-- V7: 运行总览 Dashboard 菜单指向本平台统计页
--
-- 菜单 100 (运行总览) 原指向 `dashboard/analytics/index`（haorong 商城分析页），
-- 现改为指向新建的本平台统计视图 `dashboard/platform/index`。
-- ----------------------------------------------------------------------------
SET time_zone = '+00:00';

UPDATE `sys_menu` SET `component` = 'dashboard/platform/index' WHERE `id` = 100;