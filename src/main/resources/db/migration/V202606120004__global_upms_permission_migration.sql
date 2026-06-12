-- =====================================================
-- 全局权限码迁移到 upms:<module>:<action>
-- 1. 保留菜单节点只承载路由信息，按钮节点承载授权码
-- 2. 将旧 read/write、历史自动生成按钮统一迁移为明确动作权限
-- 3. 同步角色授权、租户菜单授权与租户能力范围授权键
-- =====================================================

DROP TEMPORARY TABLE IF EXISTS `tmp_permission_grant_map`;
CREATE TEMPORARY TABLE `tmp_permission_grant_map` (
  `old_grant` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `new_grant` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`old_grant`, `new_grant`)
) ENGINE=Memory;

INSERT IGNORE INTO `tmp_permission_grant_map` (`old_grant`, `new_grant`) VALUES
  ('auth:read', 'upms:dashboard:page'),
  ('auth:read', 'upms:dashboard:get'),
  ('dashboard:read', 'upms:dashboard:page'),
  ('dashboard:read', 'upms:dashboard:get'),
  ('dashboard:page', 'upms:dashboard:page'),
  ('dashboard:get', 'upms:dashboard:get'),
  ('api.users', 'upms:sysuser:page'),
  ('api.users', 'upms:sysuser:get'),
  ('api.users', 'upms:sysuser:add'),
  ('api.users', 'upms:sysuser:edit'),
  ('api.users', 'upms:sysuser:del'),
  ('user:read', 'upms:sysuser:page'),
  ('user:read', 'upms:sysuser:get'),
  ('user:write', 'upms:sysuser:add'),
  ('user:write', 'upms:sysuser:edit'),
  ('user:write', 'upms:sysuser:del'),
  ('user:add', 'upms:sysuser:add'),
  ('user:edit', 'upms:sysuser:edit'),
  ('user:del', 'upms:sysuser:del'),
  ('user:page', 'upms:sysuser:page'),
  ('user:get', 'upms:sysuser:get'),
  ('api.roles', 'upms:sysrole:page'),
  ('api.roles', 'upms:sysrole:get'),
  ('api.roles', 'upms:sysrole:add'),
  ('api.roles', 'upms:sysrole:edit'),
  ('api.roles', 'upms:sysrole:del'),
  ('role:read', 'upms:sysrole:page'),
  ('role:read', 'upms:sysrole:get'),
  ('role:write', 'upms:sysrole:add'),
  ('role:write', 'upms:sysrole:edit'),
  ('role:write', 'upms:sysrole:del'),
  ('role:add', 'upms:sysrole:add'),
  ('role:edit', 'upms:sysrole:edit'),
  ('role:del', 'upms:sysrole:del'),
  ('role:page', 'upms:sysrole:page'),
  ('role:get', 'upms:sysrole:get'),
  ('api.depts', 'upms:sysdept:page'),
  ('api.depts', 'upms:sysdept:get'),
  ('api.depts', 'upms:sysdept:add'),
  ('api.depts', 'upms:sysdept:edit'),
  ('api.depts', 'upms:sysdept:del'),
  ('dept:read', 'upms:sysdept:page'),
  ('dept:read', 'upms:sysdept:get'),
  ('dept:write', 'upms:sysdept:add'),
  ('dept:write', 'upms:sysdept:edit'),
  ('dept:write', 'upms:sysdept:del'),
  ('dept:add', 'upms:sysdept:add'),
  ('dept:edit', 'upms:sysdept:edit'),
  ('dept:del', 'upms:sysdept:del'),
  ('dept:page', 'upms:sysdept:page'),
  ('dept:get', 'upms:sysdept:get'),
  ('tenant:read', 'upms:systenant:page'),
  ('tenant:read', 'upms:systenant:get'),
  ('tenant:read', 'upms:tenantcatalog:page'),
  ('tenant:read', 'upms:tenantcatalog:get'),
  ('tenant:write', 'upms:systenant:add'),
  ('tenant:write', 'upms:systenant:edit'),
  ('tenant:write', 'upms:systenant:del'),
  ('tenant:write', 'upms:tenantcatalog:add'),
  ('tenant:write', 'upms:tenantcatalog:edit'),
  ('tenant:write', 'upms:tenantcatalog:del'),
  ('tenant:add', 'upms:systenant:add'),
  ('tenant:edit', 'upms:systenant:edit'),
  ('tenant:del', 'upms:systenant:del'),
  ('tenant:page', 'upms:systenant:page'),
  ('tenant:get', 'upms:systenant:get'),
  ('tenant_catalog:add', 'upms:tenantcatalog:add'),
  ('tenant_catalog:edit', 'upms:tenantcatalog:edit'),
  ('tenant_catalog:del', 'upms:tenantcatalog:del'),
  ('tenant_catalog:page', 'upms:tenantcatalog:page'),
  ('tenant_catalog:get', 'upms:tenantcatalog:get'),
  ('system:read', 'upms:system:page'),
  ('system:read', 'upms:system:get'),
  ('system:read', 'upms:sysdict:page'),
  ('system:read', 'upms:sysdict:get'),
  ('system:read', 'upms:sysconfig:page'),
  ('system:read', 'upms:sysconfig:get'),
  ('system:read', 'upms:syscategory:page'),
  ('system:read', 'upms:syscategory:get'),
  ('system:read', 'upms:sysmail:page'),
  ('system:read', 'upms:sysmail:get'),
  ('system:read', 'upms:sysnotice:page'),
  ('system:read', 'upms:sysnotice:get'),
  ('system:read', 'upms:security:get'),
  ('system:write', 'upms:sysdict:add'),
  ('system:write', 'upms:sysdict:edit'),
  ('system:write', 'upms:sysdict:del'),
  ('system:write', 'upms:sysconfig:add'),
  ('system:write', 'upms:sysconfig:edit'),
  ('system:write', 'upms:sysconfig:del'),
  ('system:write', 'upms:syscategory:add'),
  ('system:write', 'upms:syscategory:edit'),
  ('system:write', 'upms:syscategory:del'),
  ('system:write', 'upms:sysmail:add'),
  ('system:write', 'upms:sysmail:edit'),
  ('system:write', 'upms:sysmail:del'),
  ('system:write', 'upms:sysnotice:add'),
  ('system:write', 'upms:sysnotice:edit'),
  ('system:write', 'upms:sysnotice:del'),
  ('system:write', 'upms:security:edit'),
  ('system:page', 'upms:system:page'),
  ('system:get', 'upms:system:get'),
  ('system:dict:add', 'upms:sysdict:add'),
  ('system:dict:edit', 'upms:sysdict:edit'),
  ('system:dict:del', 'upms:sysdict:del'),
  ('system:dict:page', 'upms:sysdict:page'),
  ('system:dict:get', 'upms:sysdict:get'),
  ('system:config:add', 'upms:sysconfig:add'),
  ('system:config:edit', 'upms:sysconfig:edit'),
  ('system:config:del', 'upms:sysconfig:del'),
  ('system:config:page', 'upms:sysconfig:page'),
  ('system:config:get', 'upms:sysconfig:get'),
  ('system:category:add', 'upms:syscategory:add'),
  ('system:category:edit', 'upms:syscategory:edit'),
  ('system:category:del', 'upms:syscategory:del'),
  ('system:category:page', 'upms:syscategory:page'),
  ('system:category:get', 'upms:syscategory:get'),
  ('system:mail_channel:add', 'upms:sysmail:add'),
  ('system:mail_channel:edit', 'upms:sysmail:edit'),
  ('system:mail_channel:del', 'upms:sysmail:del'),
  ('system:mail_channel:page', 'upms:sysmail:page'),
  ('system:mail_channel:get', 'upms:sysmail:get'),
  ('notice:add', 'upms:sysnotice:add'),
  ('notice:edit', 'upms:sysnotice:edit'),
  ('notice:del', 'upms:sysnotice:del'),
  ('notice:page', 'upms:sysnotice:page'),
  ('notice:get', 'upms:sysnotice:get'),
  ('security:read', 'upms:security:get'),
  ('security:write', 'upms:security:edit'),
  ('audit:read', 'upms:audit:page'),
  ('audit:read', 'upms:audit:get'),
  ('audit:write', 'upms:audit:export'),
  ('audit:write', 'upms:audit:download'),
  ('audit:write', 'upms:audit:edit'),
  ('audit:write', 'upms:audit:del'),
  ('audit:add', 'upms:audit:export'),
  ('audit:edit', 'upms:audit:edit'),
  ('audit:del', 'upms:audit:del'),
  ('audit:page', 'upms:audit:page'),
  ('audit:get', 'upms:audit:get'),
  ('operation-log:read', 'upms:operationlog:page'),
  ('operation-log:read', 'upms:operationlog:get'),
  ('operation-log:export', 'upms:operationlog:export'),
  ('operation_log:page', 'upms:operationlog:page'),
  ('operation_log:get', 'upms:operationlog:get'),
  ('file:read', 'upms:file:page'),
  ('file:read', 'upms:file:get'),
  ('file:write', 'upms:file:add'),
  ('file:write', 'upms:file:del'),
  ('file:add', 'upms:file:add'),
  ('file:del', 'upms:file:del'),
  ('file:page', 'upms:file:page'),
  ('file:get', 'upms:file:get'),
  ('workflow:read', 'upms:workflowdefinition:page'),
  ('workflow:read', 'upms:workflowdefinition:get'),
  ('workflow:read', 'upms:workflowinstance:page'),
  ('workflow:read', 'upms:workflowinstance:get'),
  ('workflow:read', 'upms:workflowtodo:page'),
  ('workflow:read', 'upms:workflowtodo:get'),
  ('workflow:read', 'upms:workflowdone:page'),
  ('workflow:read', 'upms:workflowdone:get'),
  ('workflow:write', 'upms:workflowdesigner:page'),
  ('workflow:write', 'upms:workflowdefinition:add'),
  ('workflow:write', 'upms:workflowdefinition:edit'),
  ('workflow:write', 'upms:workflowdefinition:deploy'),
  ('workflow:write', 'upms:workflowinstance:add'),
  ('workflow:write', 'upms:workflowinstance:edit'),
  ('workflow:write', 'upms:workflowinstance:del'),
  ('workflow:write', 'upms:workflowtodo:edit'),
  ('workflow_definition:add', 'upms:workflowdefinition:add'),
  ('workflow_definition:edit', 'upms:workflowdefinition:edit'),
  ('workflow_definition:del', 'upms:workflowdefinition:deploy'),
  ('workflow_definition:page', 'upms:workflowdefinition:page'),
  ('workflow_definition:get', 'upms:workflowdefinition:get'),
  ('workflow_instance:add', 'upms:workflowinstance:add'),
  ('workflow_instance:edit', 'upms:workflowinstance:edit'),
  ('workflow_instance:del', 'upms:workflowinstance:del'),
  ('workflow_instance:page', 'upms:workflowinstance:page'),
  ('workflow_instance:get', 'upms:workflowinstance:get'),
  ('workflow_todo:edit', 'upms:workflowtodo:edit'),
  ('workflow_todo:page', 'upms:workflowtodo:page'),
  ('workflow_todo:get', 'upms:workflowtodo:get'),
  ('workflow_done:page', 'upms:workflowdone:page'),
  ('workflow_done:get', 'upms:workflowdone:get'),
  ('workflow_designer:page', 'upms:workflowdesigner:page'),
  ('codegen:read', 'upms:codegen:page'),
  ('codegen:read', 'upms:codegen:get'),
  ('codegen:write', 'upms:codegen:add'),
  ('codegen:write', 'upms:codegen:edit'),
  ('codegen:write', 'upms:codegen:del'),
  ('codegen:download', 'upms:codegen:download'),
  ('codegen:add', 'upms:codegen:add'),
  ('codegen:edit', 'upms:codegen:edit'),
  ('codegen:del', 'upms:codegen:del'),
  ('codegen:page', 'upms:codegen:page'),
  ('codegen:get', 'upms:codegen:get'),
  ('session:write', 'upms:session:page'),
  ('session:write', 'upms:session:get'),
  ('session:write', 'upms:session:kick'),
  ('session:page', 'upms:session:page'),
  ('session:get', 'upms:session:get'),
  ('session:del', 'upms:session:kick'),
  ('menu:add', 'upms:sysmenu:add'),
  ('menu:edit', 'upms:sysmenu:edit'),
  ('menu:del', 'upms:sysmenu:del'),
  ('menu:page', 'upms:sysmenu:page'),
  ('menu:get', 'upms:sysmenu:get');

DROP TEMPORARY TABLE IF EXISTS `tmp_role_permission_source`;
CREATE TEMPORARY TABLE `tmp_role_permission_source` AS
SELECT DISTINCT
  rm.`tenant_id`,
  rm.`role_id`,
  m.`grant_key` AS `old_grant`,
  COALESCE(rm.`created_by`, 'system') AS `created_by`
FROM `sys_role_menu` rm
JOIN `sys_menu` m ON m.`id` = rm.`menu_id`
WHERE m.`tenant_id` = 'platform'
  AND m.`menu_type` = '1'
  AND m.`deleted` = 0
  AND m.`grant_key` IS NOT NULL
  AND m.`grant_key` <> '';

DROP TEMPORARY TABLE IF EXISTS `tmp_tenant_permission_source`;
CREATE TEMPORARY TABLE `tmp_tenant_permission_source` AS
SELECT DISTINCT
  tm.`tenant_id`,
  m.`grant_key` AS `old_grant`,
  COALESCE(tm.`created_by`, 'system') AS `created_by`
FROM `sys_tenant_menu` tm
JOIN `sys_menu` m ON m.`id` = tm.`menu_id`
WHERE m.`tenant_id` = 'platform'
  AND m.`menu_type` = '1'
  AND m.`deleted` = 0
  AND m.`grant_key` IS NOT NULL
  AND m.`grant_key` <> '';

DROP TEMPORARY TABLE IF EXISTS `tmp_capability_grant_source`;
CREATE TEMPORARY TABLE `tmp_capability_grant_source` AS
SELECT DISTINCT
  s.`tenant_id`,
  s.`capability_code`,
  s.`resource_key` AS `old_grant`,
  s.`required`,
  COALESCE(s.`created_by`, 'system') AS `created_by`,
  COALESCE(s.`updated_by`, 'system') AS `updated_by`
FROM `sys_tenant_capability_resource_scope` s
WHERE s.`scope_type` = 'GRANT'
  AND s.`resource_key` IS NOT NULL
  AND s.`resource_key` <> '';

DROP TEMPORARY TABLE IF EXISTS `tmp_sys_menu_permission_target`;
CREATE TEMPORARY TABLE `tmp_sys_menu_permission_target` (
  `id` bigint NOT NULL PRIMARY KEY,
  `resource_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `menu_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `grant_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_no` int NOT NULL
) ENGINE=Memory;

INSERT INTO `tmp_sys_menu_permission_target` (`id`, `resource_key`, `menu_name`, `grant_key`, `order_no`) VALUES
  (1042, 'dashboard.page', '运行总览列表', 'upms:dashboard:page', 40),
  (1043, 'dashboard.get', '运行总览查询', 'upms:dashboard:get', 50),
  (1044, 'sysuser.add', '用户管理新增', 'upms:sysuser:add', 10),
  (1045, 'sysuser.edit', '用户管理修改', 'upms:sysuser:edit', 20),
  (1046, 'sysuser.del', '用户管理删除', 'upms:sysuser:del', 30),
  (1047, 'sysuser.page', '用户管理列表', 'upms:sysuser:page', 40),
  (1048, 'sysuser.get', '用户管理查询', 'upms:sysuser:get', 50),
  (1049, 'sysrole.add', '角色管理新增', 'upms:sysrole:add', 10),
  (1050, 'sysrole.edit', '角色管理修改', 'upms:sysrole:edit', 20),
  (1051, 'sysrole.del', '角色管理删除', 'upms:sysrole:del', 30),
  (1052, 'sysrole.page', '角色管理列表', 'upms:sysrole:page', 40),
  (1053, 'sysrole.get', '角色管理查询', 'upms:sysrole:get', 50),
  (1054, 'sysdept.add', '部门管理新增', 'upms:sysdept:add', 10),
  (1055, 'sysdept.edit', '部门管理修改', 'upms:sysdept:edit', 20),
  (1056, 'sysdept.del', '部门管理删除', 'upms:sysdept:del', 30),
  (1057, 'sysdept.page', '部门管理列表', 'upms:sysdept:page', 40),
  (1058, 'sysdept.get', '部门管理查询', 'upms:sysdept:get', 50),
  (1059, 'systenant.add', '租户管理新增', 'upms:systenant:add', 10),
  (1060, 'systenant.edit', '租户管理修改', 'upms:systenant:edit', 20),
  (1061, 'systenant.del', '租户管理删除', 'upms:systenant:del', 30),
  (1062, 'systenant.page', '租户管理列表', 'upms:systenant:page', 40),
  (1063, 'systenant.get', '租户管理查询', 'upms:systenant:get', 50),
  (1064, 'audit.export', '安全审计导出', 'upms:audit:export', 10),
  (1065, 'audit.edit', '安全审计治理', 'upms:audit:edit', 20),
  (1066, 'audit.del', '安全审计删除', 'upms:audit:del', 30),
  (1067, 'audit.page', '安全审计列表', 'upms:audit:page', 40),
  (1068, 'audit.get', '安全审计查询', 'upms:audit:get', 50),
  (312, 'audit.download', '安全审计下载', 'upms:audit:download', 60),
  (1072, 'system.page', '系统设置列表', 'upms:system:page', 40),
  (1073, 'system.get', '系统设置查询', 'upms:system:get', 50),
  (1077, 'session.page', '在线用户列表', 'upms:session:page', 40),
  (1078, 'session.get', '在线用户查询', 'upms:session:get', 50),
  (315, 'session.kick', '在线用户强制下线', 'upms:session:kick', 60),
  (1034, 'sysmenu.add', '菜单管理新增', 'upms:sysmenu:add', 10),
  (1035, 'sysmenu.edit', '菜单管理修改', 'upms:sysmenu:edit', 20),
  (1036, 'sysmenu.del', '菜单管理删除', 'upms:sysmenu:del', 30),
  (1037, 'sysmenu.page', '菜单管理列表', 'upms:sysmenu:page', 40),
  (1038, 'sysmenu.get', '菜单管理查询', 'upms:sysmenu:get', 50),
  (1079, 'sysdict.add', '字典管理新增', 'upms:sysdict:add', 10),
  (1080, 'sysdict.edit', '字典管理修改', 'upms:sysdict:edit', 20),
  (1081, 'sysdict.del', '字典管理删除', 'upms:sysdict:del', 30),
  (1082, 'sysdict.page', '字典管理列表', 'upms:sysdict:page', 40),
  (1083, 'sysdict.get', '字典管理查询', 'upms:sysdict:get', 50),
  (1084, 'tenantcatalog.add', '租户套餐新增', 'upms:tenantcatalog:add', 10),
  (1085, 'tenantcatalog.edit', '租户套餐修改', 'upms:tenantcatalog:edit', 20),
  (1086, 'tenantcatalog.del', '租户套餐删除', 'upms:tenantcatalog:del', 30),
  (1087, 'tenantcatalog.page', '租户套餐列表', 'upms:tenantcatalog:page', 40),
  (1088, 'tenantcatalog.get', '租户套餐查询', 'upms:tenantcatalog:get', 50),
  (1089, 'sysconfig.add', '系统参数新增', 'upms:sysconfig:add', 10),
  (1090, 'sysconfig.edit', '系统参数修改', 'upms:sysconfig:edit', 20),
  (1091, 'sysconfig.del', '系统参数删除', 'upms:sysconfig:del', 30),
  (1092, 'sysconfig.page', '系统参数列表', 'upms:sysconfig:page', 40),
  (1093, 'sysconfig.get', '系统参数查询', 'upms:sysconfig:get', 50),
  (1094, 'sysnotice.add', '公告管理新增', 'upms:sysnotice:add', 10),
  (1095, 'sysnotice.edit', '公告管理修改', 'upms:sysnotice:edit', 20),
  (1096, 'sysnotice.del', '公告管理删除', 'upms:sysnotice:del', 30),
  (1097, 'sysnotice.page', '公告管理列表', 'upms:sysnotice:page', 40),
  (1098, 'sysnotice.get', '公告管理查询', 'upms:sysnotice:get', 50),
  (1099, 'syscategory.add', '分类配置新增', 'upms:syscategory:add', 10),
  (1100, 'syscategory.edit', '分类配置修改', 'upms:syscategory:edit', 20),
  (1101, 'syscategory.del', '分类配置删除', 'upms:syscategory:del', 30),
  (1102, 'syscategory.page', '分类配置列表', 'upms:syscategory:page', 40),
  (1103, 'syscategory.get', '分类配置查询', 'upms:syscategory:get', 50),
  (1104, 'sysmail.add', '邮件配置新增', 'upms:sysmail:add', 10),
  (1105, 'sysmail.edit', '邮件配置修改', 'upms:sysmail:edit', 20),
  (1106, 'sysmail.del', '邮件配置删除', 'upms:sysmail:del', 30),
  (1107, 'sysmail.page', '邮件配置列表', 'upms:sysmail:page', 40),
  (1108, 'sysmail.get', '邮件配置查询', 'upms:sysmail:get', 50),
  (1109, 'file.add', '文件管理上传', 'upms:file:add', 10),
  (1111, 'file.del', '文件管理删除', 'upms:file:del', 30),
  (1112, 'file.page', '文件管理列表', 'upms:file:page', 40),
  (1113, 'file.get', '文件管理查询', 'upms:file:get', 50),
  (1117, 'operationlog.page', '操作日志列表', 'upms:operationlog:page', 40),
  (1118, 'operationlog.get', '操作日志查询', 'upms:operationlog:get', 50),
  (328, 'operationlog.export', '操作日志导出', 'upms:operationlog:export', 60),
  (1119, 'workflowdefinition.add', '流程定义新增', 'upms:workflowdefinition:add', 10),
  (1120, 'workflowdefinition.edit', '流程定义修改', 'upms:workflowdefinition:edit', 20),
  (1121, 'workflowdefinition.deploy', '流程定义部署', 'upms:workflowdefinition:deploy', 30),
  (1122, 'workflowdefinition.page', '流程定义列表', 'upms:workflowdefinition:page', 40),
  (1123, 'workflowdefinition.get', '流程定义查询', 'upms:workflowdefinition:get', 50),
  (1124, 'workflowinstance.add', '流程实例发起', 'upms:workflowinstance:add', 10),
  (1125, 'workflowinstance.edit', '流程实例处理', 'upms:workflowinstance:edit', 20),
  (1126, 'workflowinstance.del', '流程实例终止', 'upms:workflowinstance:del', 30),
  (1127, 'workflowinstance.page', '流程实例列表', 'upms:workflowinstance:page', 40),
  (1128, 'workflowinstance.get', '流程实例查询', 'upms:workflowinstance:get', 50),
  (1130, 'workflowtodo.edit', '我的待办处理', 'upms:workflowtodo:edit', 20),
  (1132, 'workflowtodo.page', '我的待办列表', 'upms:workflowtodo:page', 40),
  (1133, 'workflowtodo.get', '我的待办查询', 'upms:workflowtodo:get', 50),
  (1137, 'workflowdone.page', '我的已办列表', 'upms:workflowdone:page', 40),
  (1138, 'workflowdone.get', '我的已办查询', 'upms:workflowdone:get', 50),
  (1139, 'codegen.add', '代码生成新增', 'upms:codegen:add', 10),
  (1140, 'codegen.edit', '代码生成修改', 'upms:codegen:edit', 20),
  (1141, 'codegen.del', '代码生成删除', 'upms:codegen:del', 30),
  (1142, 'codegen.page', '代码生成列表', 'upms:codegen:page', 40),
  (1143, 'codegen.get', '代码生成查询', 'upms:codegen:get', 50),
  (347, 'codegen.download', '代码生成下载', 'upms:codegen:download', 60),
  (1147, 'workflowdesigner.page', '流程设计器入口', 'upms:workflowdesigner:page', 40);

UPDATE `sys_menu` m
JOIN `tmp_sys_menu_permission_target` t ON t.`id` = m.`id`
SET m.`tenant_id` = 'platform',
    m.`menu_type` = '1',
    m.`resource_key` = t.`resource_key`,
    m.`menu_name` = t.`menu_name`,
    m.`grant_key` = t.`grant_key`,
    m.`route_key` = NULL,
    m.`path` = NULL,
    m.`component` = NULL,
    m.`redirect` = NULL,
    m.`icon` = NULL,
    m.`order_no` = t.`order_no`,
    m.`visible` = 1,
    m.`enabled` = 1,
    m.`outer_status` = 0,
    m.`deleted` = 0,
    m.`updated_by` = 'system';

INSERT INTO `sys_menu` (
  `tenant_id`, `parent_id`, `ancestors`, `menu_type`, `resource_key`, `menu_name`, `route_key`, `grant_key`,
  `path`, `component`, `redirect`, `icon`, `order_no`, `visible`, `enabled`, `is_system`, `outer_status`,
  `application_key`, `created_by`, `updated_by`, `deleted`
)
SELECT
  'platform', p.`id`,
  CASE WHEN COALESCE(p.`ancestors`, '') = '' THEN CAST(p.`id` AS CHAR) ELSE CONCAT(p.`ancestors`, ',', p.`id`) END,
  '1', 'security.get', '安全策略查询', NULL, 'upms:security:get',
  NULL, NULL, NULL, NULL, 70, 1, 1, 1, 0, NULL, 'system', 'system', 0
FROM `sys_menu` p
WHERE p.`tenant_id` = 'platform'
  AND p.`deleted` = 0
  AND p.`resource_key` = 'settings'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_menu` x WHERE x.`tenant_id` = 'platform' AND x.`resource_key` = 'security.get'
  );

INSERT INTO `sys_menu` (
  `tenant_id`, `parent_id`, `ancestors`, `menu_type`, `resource_key`, `menu_name`, `route_key`, `grant_key`,
  `path`, `component`, `redirect`, `icon`, `order_no`, `visible`, `enabled`, `is_system`, `outer_status`,
  `application_key`, `created_by`, `updated_by`, `deleted`
)
SELECT
  'platform', p.`id`,
  CASE WHEN COALESCE(p.`ancestors`, '') = '' THEN CAST(p.`id` AS CHAR) ELSE CONCAT(p.`ancestors`, ',', p.`id`) END,
  '1', 'security.edit', '安全策略修改', NULL, 'upms:security:edit',
  NULL, NULL, NULL, NULL, 80, 1, 1, 1, 0, NULL, 'system', 'system', 0
FROM `sys_menu` p
WHERE p.`tenant_id` = 'platform'
  AND p.`deleted` = 0
  AND p.`resource_key` = 'settings'
  AND NOT EXISTS (
    SELECT 1 FROM `sys_menu` x WHERE x.`tenant_id` = 'platform' AND x.`resource_key` = 'security.edit'
  );

UPDATE `sys_menu`
SET `grant_key` = NULL
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `menu_type` = '0';

UPDATE `sys_menu`
SET `path` = NULL,
    `component` = NULL,
    `redirect` = NULL,
    `route_key` = NULL,
    `icon` = NULL,
    `outer_status` = 0
WHERE `tenant_id` = 'platform'
  AND `deleted` = 0
  AND `menu_type` = '1';

INSERT INTO `sys_role_menu` (`tenant_id`, `role_id`, `menu_id`, `created_by`, `created_at`)
SELECT DISTINCT
  s.`tenant_id`,
  s.`role_id`,
  m.`id`,
  s.`created_by`,
  NOW()
FROM `tmp_role_permission_source` s
JOIN `tmp_permission_grant_map` g ON g.`old_grant` = s.`old_grant`
JOIN `sys_menu` m
  ON m.`tenant_id` = 'platform'
 AND m.`deleted` = 0
 AND m.`menu_type` = '1'
 AND m.`grant_key` = g.`new_grant`
ON DUPLICATE KEY UPDATE `created_by` = VALUES(`created_by`);

INSERT INTO `sys_tenant_menu` (`tenant_id`, `menu_id`, `created_by`, `created_at`)
SELECT DISTINCT
  s.`tenant_id`,
  m.`id`,
  s.`created_by`,
  NOW()
FROM `tmp_tenant_permission_source` s
JOIN `tmp_permission_grant_map` g ON g.`old_grant` = s.`old_grant`
JOIN `sys_menu` m
  ON m.`tenant_id` = 'platform'
 AND m.`deleted` = 0
 AND m.`menu_type` = '1'
 AND m.`grant_key` = g.`new_grant`
ON DUPLICATE KEY UPDATE `created_by` = VALUES(`created_by`);

INSERT IGNORE INTO `sys_tenant_capability_resource_scope` (
  `tenant_id`, `capability_code`, `resource_key`, `scope_type`, `required`, `created_by`, `updated_by`
)
SELECT DISTINCT
  s.`tenant_id`,
  s.`capability_code`,
  g.`new_grant`,
  'GRANT',
  s.`required`,
  s.`created_by`,
  s.`updated_by`
FROM `tmp_capability_grant_source` s
JOIN `tmp_permission_grant_map` g ON g.`old_grant` = s.`old_grant`;

INSERT IGNORE INTO `sys_tenant_capability_resource_scope` (
  `tenant_id`, `capability_code`, `resource_key`, `scope_type`, `required`, `created_by`, `updated_by`
) VALUES
  ('platform', 'role', 'upms:sysmenu:page', 'GRANT', 1, 'system', 'system'),
  ('platform', 'role', 'upms:sysmenu:get', 'GRANT', 1, 'system', 'system'),
  ('platform', 'role', 'upms:sysmenu:add', 'GRANT', 1, 'system', 'system'),
  ('platform', 'role', 'upms:sysmenu:edit', 'GRANT', 1, 'system', 'system'),
  ('platform', 'role', 'upms:sysmenu:del', 'GRANT', 1, 'system', 'system');

DROP TEMPORARY TABLE IF EXISTS `tmp_obsolete_menu_ids`;
CREATE TEMPORARY TABLE `tmp_obsolete_menu_ids` AS
SELECT m.`id`
FROM `sys_menu` m
WHERE m.`tenant_id` = 'platform'
  AND m.`menu_type` = '1'
  AND m.`deleted` = 0
  AND (m.`grant_key` IS NULL OR m.`grant_key` = '' OR m.`grant_key` NOT LIKE 'upms:%');

DELETE rm
FROM `sys_role_menu` rm
JOIN `tmp_obsolete_menu_ids` o ON o.`id` = rm.`menu_id`;

DELETE tm
FROM `sys_tenant_menu` tm
JOIN `tmp_obsolete_menu_ids` o ON o.`id` = tm.`menu_id`;

DELETE m
FROM `sys_menu` m
JOIN `tmp_obsolete_menu_ids` o ON o.`id` = m.`id`;

DELETE s
FROM `sys_tenant_capability_resource_scope` s
WHERE s.`scope_type` = 'GRANT'
  AND (s.`resource_key` IS NULL OR s.`resource_key` = '' OR s.`resource_key` NOT LIKE 'upms:%');

DROP TEMPORARY TABLE IF EXISTS `tmp_sys_menu_ancestors`;
CREATE TEMPORARY TABLE `tmp_sys_menu_ancestors` (
  `id` bigint NOT NULL PRIMARY KEY,
  `ancestors` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=Memory;

INSERT INTO `tmp_sys_menu_ancestors` (`id`, `ancestors`)
WITH RECURSIVE menu_tree AS (
  SELECT
    m.`id`,
    m.`parent_id`,
    CAST('' AS CHAR(512)) AS `ancestors`
  FROM `sys_menu` m
  WHERE m.`deleted` = 0
    AND m.`parent_id` IS NULL

  UNION ALL

  SELECT
    c.`id`,
    c.`parent_id`,
    CAST(
      CASE
        WHEN mt.`ancestors` = '' THEN CAST(c.`parent_id` AS CHAR)
        ELSE CONCAT(mt.`ancestors`, ',', c.`parent_id`)
      END AS CHAR(512)
    ) AS `ancestors`
  FROM `sys_menu` c
  JOIN menu_tree mt ON mt.`id` = c.`parent_id`
  WHERE c.`deleted` = 0
)
SELECT `id`, `ancestors`
FROM menu_tree;

UPDATE `sys_menu` m
JOIN `tmp_sys_menu_ancestors` t ON t.`id` = m.`id`
SET m.`ancestors` = t.`ancestors`
WHERE CAST(IFNULL(m.`ancestors`, '') AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci <> t.`ancestors` COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS `tmp_sys_menu_ancestors`;
DROP TEMPORARY TABLE IF EXISTS `tmp_obsolete_menu_ids`;
DROP TEMPORARY TABLE IF EXISTS `tmp_sys_menu_permission_target`;
DROP TEMPORARY TABLE IF EXISTS `tmp_capability_grant_source`;
DROP TEMPORARY TABLE IF EXISTS `tmp_tenant_permission_source`;
DROP TEMPORARY TABLE IF EXISTS `tmp_role_permission_source`;
DROP TEMPORARY TABLE IF EXISTS `tmp_permission_grant_map`;