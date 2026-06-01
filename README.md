# 企业级权限管理平台

`enterprise-auth-platform` 是一套面向企业后台场景的权限与租户管理平台。  
当前代码已经从“OAuth2/OIDC 认证中心 + 自建 JWT/Refresh Token”双轨模式，收敛为基于 Sa-Token 的企业后台模型：

- 账号密码登录
- Redis Session
- `Authorization: Bearer` Header Token
- RBAC
- 多租户隔离
- 审计与会话治理

## 当前状态

当前主认证链路：

1. 前端调用 `POST /api/auth/login`
2. 后端校验租户、账号、密码、验证码与登录风控
3. 后端创建 Sa-Token Redis Session，并返回 Bearer token
4. 前端用 `Authorization: Bearer <token>` 调用 `GET /api/auth/me` 恢复用户、菜单和权限快照

当前设计约束：

- 浏览器不再持有自建 `access_token` / `refresh_token`
- 主认证链路不依赖 Cookie，不启用 CSRF Token
- 后端主链路不再依赖 OAuth2 Authorization Server
- 角色资源授权保存于 `sys_role_resource`
- 自定义数据范围直接保存 `data_scope_value_json`
- 用户名唯一性为全局约束：`username`
- 改密、禁用、强制下线通过会话版本和 Redis Session 实现

## 技术栈

### 后端

- Spring Boot 3.5.11
- Sa-Token
- Spring Security Crypto
- MyBatis-Plus
- MySQL 8.0 + HikariCP
- Redis / Redisson
- EasyExcel

### 前端

- Vue 3 + TypeScript + Vite
- Element Plus + Pinia + Vue Router + Axios
- Sass + ECharts
- Vitest + Playwright

## 核心能力

### 认证与授权

- 基于 Sa-Token Header Token 的企业后台登录
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
- `sys_resource`
- `sys_role_resource`
- `sys_tenant_resource_override`

角色表关键字段：

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
- `GET /api/auth/register/options`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/sessions`
- `POST /api/auth/sessions/{sessionId}/offline`
- `POST /api/auth/register`

详细认证与会话策略见 [docs/security-auth.md](docs/security-auth.md)。

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
- `GET /api/roles/{roleId}/resources`
- `PUT /api/roles/{roleId}/resources`
- `GET /api/depts`
- `POST /api/depts`
- `PUT /api/depts/{deptId}`
- `DELETE /api/depts/{deptId}`
- `GET /api/resources/tree`
- `POST /api/resources`
- `PUT /api/resources/{resourceId}`
- `DELETE /api/resources/{resourceId}`
- `PUT /api/resources/{resourceId}/sort`
- `GET /api/tenants/{tenantId}/resource-overrides`
- `PUT /api/tenants/{tenantId}/resource-overrides`

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

首次本地启动前，先按“本地依赖与配置”准备数据库和 Redis 连接；应用启动时会默认执行 `src/main/resources/db/migration` 下的 Flyway 迁移。

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

或：

```bash
mvn clean package
java -jar target/enterprise-auth-platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
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

- 默认运行配置：`src/main/resources/application.yml`，不包含 DB/Redis 明文默认值，必须通过环境变量注入。
- 开发环境配置：`src/main/resources/application-dev.yml`，面向共享开发环境或 CI 开发部署，允许较小连接池和本地前端 CORS。
- 预发环境配置：`src/main/resources/application-staging.yml`，使用 `STAGING_*` 环境变量覆盖 DB、Redis、CORS 与会话策略。
- 生产配置补充：`src/main/resources/application-prod.yml`，默认关闭接口文档，仅保留健康检查暴露。
- 可选本地覆盖：`src/main/resources/application-local.yml`。如需 `local` profile，可复制 `src/main/resources/application-local.example.yml` 后仅覆写本机 DB/Redis/Flyway 参数；该文件已加入 `.gitignore`，不要提交。

本地启动示例：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/enterprise_auth_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='<your-local-password>'
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='6379'
$env:REDIS_PASSWORD=''
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

也可以直接通过环境变量提供连接信息：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `REDIS_PASSWORD`
- `REDIS_DATABASE`
- `APP_CORS_ALLOWED_ORIGIN`
- `DEV_*`：开发环境专用覆盖，如 `DEV_DB_URL`、`DEV_REDIS_HOST`、`DEV_APP_CORS_ALLOWED_ORIGIN`
- `STAGING_*`：预发环境专用覆盖，如 `STAGING_DB_URL`、`STAGING_REDIS_HOST`、`STAGING_APP_CORS_ALLOWED_ORIGIN`
- `APP_SECURITY_SESSION_IDLE_SECONDS`
- `APP_SECURITY_MAX_LOGIN_COUNT`
- `PROD_KNIFE4J_ENABLED`
- `FLYWAY_ENABLED`
- `FLYWAY_BASELINE_ON_MIGRATE`

数据库迁移：

- 初始化与增量迁移统一放在 `src/main/resources/db/migration/`
- `V1__baseline.sql` 是当前库结构与基础数据基线
- 本地已手工导入旧 SQL 且库非空时，首次切换 Flyway 前可设置 `FLYWAY_BASELINE_ON_MIGRATE=true`
- `src/main/resources/database/enterprise_auth_platform.sql` 仅保留作基线来源与兼容备份，不再作为主初始化路径

## 构建与测试

### 后端

```bash
mvn "-Dmaven.repo.local=.m2repo" compile
mvn "-Dmaven.repo.local=.m2repo" test
mvn "-Dmaven.repo.local=.m2repo" verify
```

后端测试依赖可连接的 MySQL 与 Redis；测试启动时会自动执行 Flyway 迁移。`src/test/resources/application.yml` 默认使用本机 MySQL/Redis，并已开启 `baseline-on-migrate` 兼容本地非空历史库；如需切换到其他环境，可用 `TEST_*` 环境变量覆盖：

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3306/enterprise_auth_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true'
$env:TEST_DB_USERNAME='root'
$env:TEST_DB_PASSWORD='<your-local-password>'
$env:TEST_REDIS_HOST='127.0.0.1'
$env:TEST_REDIS_PORT='6379'
$env:TEST_REDIS_PASSWORD=''
mvn "-Dmaven.repo.local=.m2repo" test
```

数据库结构和基础数据由 Flyway 在测试启动时自动迁移；如本地测试库已经手工导入过旧 SQL，测试配置会将其 baseline 到版本 1 后继续应用后续增量脚本。

### 前端

```bash
cd frontend
npm run lint
npm run test:unit
npm run build
```

前端单元测试使用 Vitest，优先覆盖 store、composable、权限判断与通用交互状态；E2E 继续覆盖登录、菜单、会话和关键 CRUD 流程。

### E2E / 视觉回归

```bash
cd frontend
npm run test:e2e
npm run test:visual
npm run test:visual:update
```

## 项目维护文档

- 模块边界和依赖方向：参考 `docs/模块边界规范.md`
- 认证、会话、权限、租户切换：参考 `docs/security-auth.md`

## CI

- 主 CI：`.github/workflows/ci.yml`，覆盖后端 verify、前端 lint、Vitest 单测、前端 build 与 E2E。
- 前端视觉回归：`.github/workflows/frontend-visual-regression.yml`

## 部署前检查

- 参考 `DEPLOYMENT_CHECKLIST.md`
- 重点检查：DB、Redis、`allowed-origins`、租户参数、Bearer Token 与会话超时策略

## 视觉快照策略

- 视觉快照由 CI 或本地 `npm run test:visual:update` 生成，统一作为 Artifact 或本地临时产物处理，不提交源码。
- `playwright-report/`、`test-results/` 与 `frontend/e2e/**/*.png` 均不提交源码。
- 如需人工确认 UI 变化，从 CI Artifact 下载快照对比，确认后再单独决定是否建立受控基线策略。

## 最近进展（2026-06-01）

- 模块化基座改造全部完成：旧的 `controller/service/dao/dto` 大平层已清空，8 个业务模块（auth/user/role/resource/dept/tenant/system/audit）全部按 `interfaces/application/domain/infrastructure` 分层就位。
- 过渡包清空：`config/` 和 `security/` 两个历史过渡包已拆解归属，不再存在。
- 跨模块依赖治理完成：所有模块的 application 层不再直接注入其他模块的 Mapper/Entity，统一通过 facade 协作。`ModuleBoundaryTest`（3 个 ArchUnit 规则）全量守护。
- 缓存治理：缓存名称已收敛到 `common/cache` 统一登记入口，认证主体、注册策略、系统字典、参数、公告和分类缓存具备独立 TTL 配置。
- 权限码治理：后端 Controller 权限注解已统一引用 `PermissionCodes`，前端权限声明由模块 manifest 消费。
- 数据迁移治理：Flyway 已接管 CI、启动与部署主路径，V1 基线与审计索引增量迁移已落地。
- 系统域拆分：`SystemManagementService` 过渡引用已清理，系统域由字典、参数、公告、分类规则四个 application service 承接。
- 项目结构整理：根目录 Node 残留依赖文件已移除，前端依赖收敛到 `frontend/`。
- 配置安全整理：DB/Redis 连接信息改为环境变量覆盖，测试配置统一为 YAML。
- 文档整理：过时 OAuth2/OIDC 与旧 Session-Cookie 文档已归档到 `docs/archive/`；已完成的优化计划清单已移除。
- 前端 API 结构统一：业务 API 统一放入 `frontend/src/api/modules/`，业务代码统一从 `@/api/modules` 导入。
- CI 视觉回归改为默认对比模式，基线更新只在人工确认时执行。

## 后续方向

- 安全增强：敏感字段脱敏、异常登录风控、高价值操作二次确认或防重放。
- 功能完善：用户/部门批量导入导出、通知中心增强、租户目录影响分析持续完善。
- 性能优化：关键查询索引、慢查询治理、缓存命中率观测、前端路由分包。
- 运维支持：监控接入、配置中心、网关和分布式任务能力按需启用。

## 说明

- 本 README 以当前代码现状为准。
- 认证与会话策略以 `docs/security-auth.md` 为准。
- 部署前检查以 `DEPLOYMENT_CHECKLIST.md` 为准。
- 过时 OAuth2/OIDC 与旧 Session-Cookie 资料已归档到 `docs/archive/`，仅作为历史背景参考。
