# 企业级权限管理平台

`enterprise-auth-platform` 是一套面向企业后台场景的权限与租户管理平台。  
当前代码已经从“OAuth2/OIDC 认证中心 + 自建 JWT/Refresh Token”双轨模式，收敛为更轻量的企业后台模型：

- 账号密码登录
- Redis Session
- HttpOnly Cookie
- CSRF 防护
- RBAC
- 多租户隔离
- 审计与会话治理

## 当前状态

当前主认证链路：

1. 前端调用 `POST /api/auth/login`
2. 后端校验租户、账号、密码、验证码与登录风控
3. 后端创建 Redis Session，并下发 `sid` Cookie
4. 前端通过 `GET /api/auth/me` 恢复用户、菜单和权限快照

当前设计约束：

- 浏览器不再持有 `access_token` / `refresh_token`
- 后端主链路不再依赖 OAuth2 Authorization Server
- 角色直接保存 `permissions_json`
- 自定义数据范围直接保存 `data_scope_value_json`
- 用户名唯一性按租户约束：`(tenant_id, username)`
- 改密、禁用、强制下线通过会话版本和 Redis Session 实现

## 技术栈

### 后端

- Spring Boot 3.2.5
- Spring Security
- MyBatis-Plus
- MySQL 8.0 + HikariCP
- Redis / Redisson
- EasyExcel

### 前端

- Vue 3 + TypeScript + Vite
- Element Plus + Pinia + Vue Router + Axios
- Sass + ECharts
- Playwright

## 核心能力

### 认证与授权

- 基于 Session Cookie 的企业后台登录
- 验证码、登录失败限制、会话管理、强制下线
- RBAC 授权模型
- 权限快照恢复与菜单动态渲染

### 平台权限与组织

- 用户管理
- 角色管理
- 部门管理
- 多租户隔离与租户上下文透传

### 系统管理

- 字典管理
- 参数配置
- 公告管理
- 分类规则管理
- 前端统一表格偏好、筛选区、详情抽屉交互

### 审计与导出治理

- 审计事件查询与导出
- 审计导出任务创建、重试、归档、清理
- 导出保留策略与治理任务
- `dryRun` 预演支持

### 租户目录能力

- 套餐管理
- 能力管理
- 租户能力覆盖
- 变更影响分析

## 主要数据模型

运行时核心表：

- `sys_tenant`
- `sys_user`
- `sys_role`
- `sys_user_role`

角色表关键字段：

- `permissions_json`
- `data_scope_type`
- `data_scope_value_json`

已从运行时摘除、通过迁移脚本清理的旧表：

- `oauth2_authorization`
- `oauth2_authorization_consent`
- `sys_oauth_client`
- `sys_oauth_client_history`
- `sys_oauth_scope`
- `sys_permission`
- `sys_role_permission`
- `sys_role_dept_scope`

## 主要接口

### 认证

- `GET /api/auth/captcha`
- `GET /api/auth/csrf`
- `GET /api/auth/register/options`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/sessions`
- `POST /api/auth/sessions/{sessionId}/offline`
- `POST /api/auth/register`

### 用户、角色、部门

- `GET /api/users`
- `POST /api/users`
- `PUT /api/users/{userId}`
- `DELETE /api/users/{userId}`
- `GET /api/users/{userId}/roles`
- `PUT /api/users/{userId}/roles`
- `GET /api/roles`
- `POST /api/roles`
- `PUT /api/roles/{roleId}`
- `DELETE /api/roles/{roleId}`
- `GET /api/roles/{roleId}/permissions`
- `PUT /api/roles/{roleId}/permissions`
- `GET /api/permissions`
- `GET /api/depts`
- `POST /api/depts`
- `PUT /api/depts/{deptId}`
- `DELETE /api/depts/{deptId}`

### 租户与租户目录

- `GET /api/tenants`
- `POST /api/tenants`
- `PUT /api/tenants/{tenantId}`
- `DELETE /api/tenants/{tenantId}`
- `GET /api/tenants/{tenantId}/history`
- `GET /api/tenants/{tenantId}/history/summary`
- `GET /api/tenants/{tenantId}/capability-overrides`
- `PUT /api/tenants/{tenantId}/capability-overrides`
- `GET /api/tenants/current`
- `GET /api/tenant-catalog/packages`
- `POST /api/tenant-catalog/packages`
- `PUT /api/tenant-catalog/packages/{id}`
- `GET /api/tenant-catalog/packages/{id}/impact`
- `DELETE /api/tenant-catalog/packages/{id}`
- `GET /api/tenant-catalog/capabilities`
- `POST /api/tenant-catalog/capabilities`
- `PUT /api/tenant-catalog/capabilities/{id}`
- `GET /api/tenant-catalog/capabilities/{id}/impact`
- `DELETE /api/tenant-catalog/capabilities/{id}`

### 系统管理

- `GET /api/system/features`
- `GET /api/system/categories`
- `GET /api/system/categories/{targetType}`
- `GET /api/system/categories/{targetType}/{code}/analysis`
- `POST /api/system/categories/{targetType}`
- `PUT /api/system/categories/{targetType}/{code}`
- `DELETE /api/system/categories/{targetType}/{code}`
- `GET /api/system/dicts`
- `POST /api/system/dicts`
- `PUT /api/system/dicts/{id}`
- `DELETE /api/system/dicts/{id}`
- `GET /api/system/configs`
- `POST /api/system/configs`
- `PUT /api/system/configs/{id}`
- `DELETE /api/system/configs/{id}`
- `GET /api/system/notices`
- `POST /api/system/notices`
- `PUT /api/system/notices/{id}`
- `DELETE /api/system/notices/{id}`

### 审计与导出

- `GET /api/audit/events`
- `GET /api/audit/events/export`
- `POST /api/audit/exports`
- `GET /api/audit/exports`
- `GET /api/audit/exports/policy`
- `PUT /api/audit/exports/policy`
- `POST /api/audit/exports/governance`
- `GET /api/audit/exports/{taskId}/download`
- `POST /api/audit/exports/{taskId}/archive`
- `POST /api/audit/exports/archive`
- `POST /api/audit/exports/{taskId}/retry`
- `DELETE /api/audit/exports/{taskId}`
- `DELETE /api/audit/exports`

## 启动方式

### 后端

```bash
mvn spring-boot:run
```

或：

```bash
mvn clean package
java -jar target/enterprise-auth-platform-0.0.1-SNAPSHOT.jar
```

默认端口：`8080`

### 前端

```bash
cd frontend
npm install
npm run dev
```

默认端口：`5173`

## 本地依赖与配置

- MySQL：`127.0.0.1:3306`
- Redis：`127.0.0.1:6379`
- 默认数据库与安全配置：`src/main/resources/application.yml`
- 生产配置补充：`src/main/resources/application-prod.yml`

数据库脚本：

- 初始化脚本：`src/main/resources/database/enterprise_auth_platform.sql`

## 构建与测试

### 后端

```bash
mvn "-Dmaven.repo.local=.m2repo" compile
mvn "-Dmaven.repo.local=.m2repo" test
mvn "-Dmaven.repo.local=.m2repo" verify
```

### 前端

```bash
cd frontend
npm run lint
npm run build
```

### E2E / 视觉回归

```bash
cd frontend
npm run test:e2e
npm run test:visual
npm run test:visual:update
```

## CI

- 主 CI：`.github/workflows/ci.yml`
- 前端视觉回归：`.github/workflows/frontend-visual-regression.yml`

## 部署前检查

- 参考 `DEPLOYMENT_CHECKLIST.md`
- 重点检查：DB、Redis、`allowed-origins`、租户参数、Cookie 安全策略

## 视觉快照策略

- 不提交 `frontend/e2e/visual-baseline.spec.ts-snapshots/`
- CI 中动态生成视觉快照
- 快照与报告统一作为 Artifact 上传

## 最近进展（2026-03-23）

### 已完成
- 前端统一抽屉模板扩展至更多管理页。
- `ConsentsView` 与 `TenantCatalogView` 补齐表格偏好能力（列显隐、列宽记忆、密度切换、恢复默认）。
- 新增 E2E 回归覆盖上述关键流程。
- 新增 CORS 回归测试：`CorsSecurityRegressionTest`（允许配置域名、拒绝 `null` Origin、不影响登录表单）。
- 后端新增：
  - 套餐/能力 impact-analysis 接口。
  - OAuth2 client/scope linkage 联动引导接口。
  - 审计导出自动治理接口与自动触发机制。
- 后端回归测试补齐：`AuditControllerTest` 覆盖治理接口 `dryRun` 与执行模式。
- 新增主 CI 工作流：后端 `mvn verify`、前端 `lint/build`、前端 E2E。

### 待继续（2026-03-27 更新）
一、安全性增强 🔴 高优先级
| 序号 | 功能 | 说明 |
|-----|------|------|
| 1 | 密码策略加强 | ✅ 已实现（最少8位，包含字母和数字） |
| 2 | 登录增强 | ✅ 已实现（LoginAttemptService） |
| 3 | 会话管理 | ✅ 部分实现（会话超时、强制下线） |
| 4 | 敏感数据脱敏 | 日志/返回中敏感信息（手机号、身份证）掩码 |
| 5 | API 限流 | 防止暴力请求 |
| 6 | 安全日志 | 记录安全相关事件（非法访问、权限绕过尝试） |

二、功能完善 🟡 中优先级
| 序号 | 功能 | 说明 |
|-----|------|------|
| 1 | ~~第三方登录~~ | 🚫 暂不做（微信、钉钉、企业微信 OAuth 集成） |
| 2 | ~~短信/邮箱验证码登录~~ | 🚫 暂不做 |
| 3 | 用户注册 | ✅ 已实现（自助注册，默认分配到 tenant-a 租户） |
| 4 | ~~权限委托~~ | 🚫 暂不做 |
| 5 | 消息通知中心 | ✅ 已实现（公告管理） |
| 6 | 数据导入导出 | 用户、部门等批量导入导出（审计导出已实现） |

三、性能优化 🟡 中优先级
| 序号 | 功能 | 说明 |
|-----|------|------|
| 1 | 多级缓存 | 本地缓存 + Redis 二级缓存 |
| 2 | 数据库优化 | 关键查询索引优化、慢查询监控 |
| 3 | 分布式部署 | 支持多实例部署、会话共享 |
| 4 | 前端优化 | 懒加载、路由分包 |
| 5 | 异步任务队列 | 重要任务异步化 |

四、运维支持 🟢 低优先级
| 序号 | 功能 | 说明 |
|-----|------|------|
| 1 | 监控接入 | Spring Boot Admin 完善 |
| 2 | 配置中心 | Nacos 配置管理（项目已预留依赖） |
| 3 | 网关 | Spring Cloud Gateway（项目已预留依赖） |
| 4 | 分布式事务 | Seata（项目已预留依赖） |
---



## 最近改造结果

- 已切换主登录链路到 Session Cookie
- 已移除前后端 OAuth Client / Scope / Consent 管理入口
- 已移除后端 JWT / Authorization Server 主路径依赖
- 已移除权限旧表运行时依赖，角色权限改为 `permissions_json`
- 已移除自定义部门旧表运行时依赖，角色数据范围改为 `data_scope_value_json`

## 说明

- 本 README 以当前代码现状为准
- 如果历史截图、旧设计稿或旧文档与这里不一致，以代码和本文件为准
