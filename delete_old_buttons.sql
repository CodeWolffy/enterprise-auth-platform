-- =====================================================
-- 删除旧按钮，保留5个标准按钮，与截图对齐
-- =====================================================

-- 1. 先从 sys_role_menu 中删除旧按钮的引用
DELETE FROM `sys_role_menu` WHERE `menu_id` IN (210, 211, 212, 220, 230, 240, 250, 260, 346);

-- 2. 删除旧按钮（标记为逻辑删除）
UPDATE `sys_menu` SET `deleted` = 1, `updated_by` = 'system', `updated_at` = NOW() WHERE `id` IN (210, 211, 212, 220, 230, 240, 250, 260, 346);

-- 3. 更新 sys_menu 自增值
ALTER TABLE `sys_menu` AUTO_INCREMENT = 1179;