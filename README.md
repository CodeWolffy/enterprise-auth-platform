# 企业级权限管理平台（enterprise-auth-platform）

当前阶段：数据库落地、认证中心可用、前后端联调完成，管理端持续打磨中。

## 技术栈

### 后端
- Spring Boot 3.2.5
- Spring Security + Spring Authorization Server
- MyBatis-Plus
- MySQL 8.0
- Redis / Redisson
- EasyExcel（审计导出）

### 前端
- Vue 3 + TypeScript + Vite
- Element Plus + Pinia + Vue Router + Axios
- Sass + ECharts
- Playwright（E2E / 视觉回归）

---

## 核心能力

### 认证与授权
- OAuth2 / OIDC 授权能力可用（授权、令牌、JWK）。
- OAuth2 客户端管理：增删改查、启停、密钥轮换、状态历史。
- OAuth2 作用域管理。
- 授权记录（consents）查询与撤销。

### 平台权限与组织
- 用户、角色、权限（RBAC）管理。
- 部门管理。
- 多租户隔离与租户上下文透传（含平台租户）。

### 系统管理
- 字典、配置、公告、分类等系统配置管理。
- 前端管理页统一交互（筛选区、表格偏好、详情抽屉、状态反馈）。

### 审计与导出治理
- 审计事件查询与导出。
- 异步导出任务：创建、列表、下载、重试、单条归档、批量归档、清理。
- 导出保留策略：查询、更新。
- 自动治理策略（已落地）：按 `retentionDays` + `maxTasks` 执行归档/清理。
- 治理支持 `dryRun` 预演。

### 租户目录能力（套餐/能力）
- 套餐与能力的增删改查。
- 套餐/能力变更影响分析（impact-analysis）。

### OAuth2 联动引导（已落地）
- 客户端侧 scope 联动分析。
- 作用域侧 client 联动分析。

---

## 主要接口（按模块）

### 认证
- `GET /api/auth/captcha`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/sessions`
- `POST /api/auth/sessions/{sessionId}/offline`

### OAuth2 客户端与作用域
- `GET /api/oauth-clients`
- `GET /api/oauth-clients/{id}`
- `GET /api/oauth-clients/{id}/scope-linkage`
- `POST /api/oauth-clients`
- `PUT /api/oauth-clients/{id}`
- `PUT /api/oauth-clients/{id}/status`
- `POST /api/oauth-clients/{id}/rotate-secret`
- `DELETE /api/oauth-clients/{id}`
- `GET /api/oauth-scopes`
- `GET /api/oauth-scopes/{id}/client-linkage`
- `POST /api/oauth-scopes`
- `PUT /api/oauth-scopes/{id}`
- `DELETE /api/oauth-scopes/{id}`

### 授权记录
- `GET /api/auth/consents`
- `DELETE /api/auth/consents`

### 租户与租户目录
- `GET /api/tenants`
- `PUT /api/tenants/{tenantId}`
- `DELETE /api/tenants/{tenantId}`
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

### 审计与导出
- `GET /api/audit/events`
- `GET /api/audit/events/export`
- `POST /api/audit/exports`
- `GET /api/audit/exports`
- `GET /api/audit/exports/{taskId}/download`
- `POST /api/audit/exports/{taskId}/retry`
- `POST /api/audit/exports/{taskId}/archive`
- `POST /api/audit/exports/archive`
- `DELETE /api/audit/exports/{taskId}`
- `DELETE /api/audit/exports`
- `GET /api/audit/exports/policy`
- `PUT /api/audit/exports/policy`
- `POST /api/audit/exports/governance`

---

## 启动方式

### 后端
```bash
mvn spring-boot:run
```
或
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

---

## 本地依赖与配置

- MySQL：`127.0.0.1:3306`
- Redis：`127.0.0.1:6379`
- 默认数据库连接见 `src/main/resources/application.yml`

数据库初始化脚本：
- `src/main/resources/database/enterprise_auth_platform.sql`

---

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

### CI 工作流
- 主 CI：`.github/workflows/ci.yml`（后端 verify + 前端 lint/build + 前端 E2E）。
- 前端视觉回归：`.github/workflows/frontend-visual-regression.yml`。

---

## 部署前检查清单

- 见 `DEPLOYMENT_CHECKLIST.md`（覆盖 DB、Redis、issuer、allowed-origins、回调地址、租户参数等）。

---

## 视觉快照策略（现行唯一）

- 不将 `frontend/e2e/visual-baseline.spec.ts-snapshots/` 提交到 Git。
- CI 动态生成快照：执行 `npm run test:visual:update`。
- 快照与报告统一作为 Artifact 上传用于 PR 审阅。
- 工作流文件：`.github/workflows/frontend-visual-regression.yml`。

---

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

### 待继续
- 统一清理后端注解描述和文档中的历史编码乱码（不影响功能，但影响可读性）。

---

## 说明

- 当前 README 已按代码现状整理；如与历史截图/旧文档存在差异，以本文件与代码为准。
