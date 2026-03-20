# 企业级权限管理平台

当前仓库已经进入“真实数据库运行 + Spring Authorization Server 认证中心 + 前后端联调 + 领域表拆分”的阶段。

后端基于：
- Spring Boot 3.2
- Spring Security
- Spring Authorization Server
- MyBatis-Plus
- MySQL 8.0
- Redis / Redisson

前端位于 [frontend](./frontend)，技术栈为：
- Vue 3
- TypeScript
- Vite
- Element Plus
- Pinia
- Vue Router
- Axios
- Sass
- ECharts

## 当前完成范围

### 后端
- 模块化单体结构已落地：`auth / user / role / permission / dept / tenant / audit / system`
- MySQL 作为真实主数据源，Redis / Redisson 已接入
- Spring Authorization Server 可运行：
  - `/.well-known/openid-configuration`
  - `/oauth2/authorize`
  - `/oauth2/token`
  - `/oauth2/jwks`
  - `/login`
  - `/oauth2/consent`
- OAuth2 客户端数据库化管理：
  - 列表、详情、创建、修改、启停、删除
  - 密钥轮换
  - 授权记录联动
  - 作用域说明、作用域类型统计、接入建议
  - 客户端状态历史
- OAuth2 作用域独立管理
- RBAC + 数据权限已覆盖首期核心链路
- 多租户上下文透传 + MyBatis-Plus 多租户拦截
- 审计日志落库、筛选、同步导出、异步导出
- 审计异步导出任务支持：
  - 进度展示
  - 失败重试
  - 结果归档
  - 批量归档
  - 批量清理
  - 保留策略配置
- 租户管理支持：
  - 服务端分页 / 筛选
  - 变更历史分页
  - 变更历史摘要
  - 近期轨迹时间线
  - 套餐变更影响说明
  - 套餐 / 能力展示
  - 租户能力覆盖管理
- 用户、角色、权限、部门、租户、字典、参数、公告基础 CRUD
- 系统分类规则已拆为独立领域模型

### 前端
- 控制台骨架已落地
- OAuth2 登录、授权记录、客户端管理已联调
- 用户、角色、权限、部门、租户、审计页面已联调
- 系统管理已拆分为独立页面：
  - 字典管理
  - 参数管理
  - 公告管理
  - 分类规则管理
- 新增领域页面已联调：
  - OAuth2 作用域管理
  - 租户套餐与能力管理
- 租户页已接入：
  - 服务端分页
  - 历史摘要
  - 轨迹时间线
  - 能力覆盖
- 审计页已接入：
  - 异步导出任务进度
  - 保留策略
  - 单条归档
  - 批量归档
  - 失败重试
- 客户端详情页已接入：
  - 作用域详情
  - 类型统计
  - 接入建议
  - 状态历史

## 新增领域表

以下领域数据已从旧的通用配置 / 字典 / 审计复用方式中拆出，改为独立领域表：

- `sys_oauth_scope`
- `sys_oauth_client_history`
- `sys_tenant_package`
- `sys_tenant_capability`
- `sys_tenant_package_capability`
- `sys_tenant_capability_override`
- `sys_category_rule`
- `sys_audit_export_policy`
- `sys_role_dept_scope`

同时 `sys_tenant` 已补充：
- `package_code`
- `lifecycle_note`

## 主要接口

### OAuth2 作用域管理
- `GET /api/oauth-scopes`
- `POST /api/oauth-scopes`
- `PUT /api/oauth-scopes/{id}`
- `DELETE /api/oauth-scopes/{id}`

### OAuth2 客户端管理
- `GET /api/oauth-clients`
- `GET /api/oauth-clients/{id}`
- `POST /api/oauth-clients`
- `PUT /api/oauth-clients/{id}`
- `PUT /api/oauth-clients/{id}/status`
- `POST /api/oauth-clients/{id}/rotate-secret`
- `DELETE /api/oauth-clients/{id}`

### 租户套餐与能力管理
- `GET /api/tenant-catalog/packages`
- `POST /api/tenant-catalog/packages`
- `PUT /api/tenant-catalog/packages/{id}`
- `DELETE /api/tenant-catalog/packages/{id}`
- `GET /api/tenant-catalog/capabilities`
- `POST /api/tenant-catalog/capabilities`
- `PUT /api/tenant-catalog/capabilities/{id}`
- `DELETE /api/tenant-catalog/capabilities/{id}`

### 租户能力覆盖与历史
- `GET /api/tenants/{tenantId}/history`
- `GET /api/tenants/{tenantId}/history/summary`
- `GET /api/tenants/{tenantId}/capability-overrides`
- `PUT /api/tenants/{tenantId}/capability-overrides`

### 审计导出
- `GET /api/audit/events`
- `GET /api/audit/events/export`
- `POST /api/audit/exports`
- `GET /api/audit/exports`
- `POST /api/audit/exports/{taskId}/retry`
- `POST /api/audit/exports/{taskId}/archive`
- `POST /api/audit/exports/archive`
- `DELETE /api/audit/exports/{taskId}`
- `DELETE /api/audit/exports`
- `GET /api/audit/exports/policy`
- `PUT /api/audit/exports/policy`

## 启动方式

### 后端
配置文件：
- [application.yml](./src/main/resources/application.yml)

启动命令：
```bash
mvn spring-boot:run
```

或：
```bash
mvn clean package
java -jar target/enterprise-auth-platform-0.0.1-SNAPSHOT.jar
```

默认端口：
- `8080`

### 前端
目录：
- [frontend](./frontend)

启动命令：
```bash
cd frontend
npm install
npm run dev
```

默认端口：
- `5173`

## 数据库初始化

主初始化脚本：
- [enterprise_auth_platform.sql](./src/main/resources/database/enterprise_auth_platform.sql)

领域表升级脚本：
- [upgrade_20260320_domain_tables.sql](./src/main/resources/database/upgrade_20260320_domain_tables.sql)

系统索引升级脚本：
- [upgrade_20260320_system_indexes.sql](./src/main/resources/database/upgrade_20260320_system_indexes.sql)

当前环境说明：
- 领域表升级脚本已执行
- 系统索引升级脚本已执行

默认数据库连接：
- `jdbc:mysql://127.0.0.1:3306/enterprise_auth_platform`
- 用户名：`root`
- 密码：`123456`

## 测试与构建

后端测试：
```bash
mvn "-Dmaven.repo.local=.m2repo" test
```

当前结果：
- `55` 个测试通过
- `4` 个测试因无 Docker 自动跳过

前端检查：
```bash
cd frontend
npm run lint
npm run build
```

## 当前数据库设计边界

以下领域数据已经明确不再继续硬塞进旧表：
- OAuth2 作用域说明
- OAuth2 客户端状态历史
- 租户套餐
- 租户能力
- 套餐与能力关系
- 租户能力覆盖
- 系统分类规则
- 审计导出保留策略
- 角色自定义部门范围

以下内容当前仍保留在原表中，且仍然合理：
- 普通系统参数：`sys_config`
- 普通字典项：`sys_dict`
- 通用审计事件：`sys_audit_log`
- OAuth2 客户端主表：`sys_oauth_client`
- SAS 标准授权表：
  - `oauth2_authorization`
  - `oauth2_authorization_consent`

## 当前仍待完善

### 前端
- 租户编辑页中“套餐选择 + 能力覆盖”联动还可以继续细化
- OAuth2 作用域管理、套餐 / 能力管理还可以补更完整的详情抽屉和引用提示
- 审计导出任务还可以补更细的归档结果说明和到期处理提示

### 后端
- 租户套餐 / 能力变更还可以补更细的时间线摘要
- OAuth2 作用域与客户端详情联动还可以继续增强到更细的授权引导
- 审计异步导出还可以继续补归档后的二次治理策略
- 系统分类规则还可以继续补引用分析和审计联动深化

### 菜单模型

当前前端仍使用 `menu.code + 本地路由元数据` 兜底动态菜单标题与页面映射。  
这是当前有意保留的策略，后续需要单独收口：

- 后端菜单模型统一为稳定的 `code / path / component / order / hidden / parentCode`
- 前端改为完全由后端菜单模型驱动
- 菜单层级、排序、隐藏页、按钮权限一起整理

## 下一步建议

1. 租户套餐 / 能力变更的历史轨迹继续深化到更完整的时间线视图
2. OAuth2 作用域说明与客户端详情联动继续增强到更细的授权引导
3. 审计异步导出任务的归档结果、到期处理和保留策略继续产品化
4. 系统分类规则的引用分析、趋势视图和审计联动继续增强
5. 后续再单独收口菜单模型，不在当前阶段硬改目录服务
