# 企业级管理后台基座（Enterprise Admin Base）

`enterprise-auth-platform` 是一套**企业级管理后台基座**：不是单一的权限管理平台，而是用于快速孵化各类企业管理后台的通用底座。基座内置认证、RBAC、多租户、组织架构、系统管理、日志、通知、文件、工作流、代码生成等横向能力，业务系统在此之上做增量开发。

> 说明：仓库名与 Java 包名 `com.enterprise.auth.platform` 沿用了项目早期"认证平台"阶段的命名，当前定位以本 README 为准；包名改名成本较高，计划在后续大版本统一处理。

## 技术栈

### 后端

- Java 17 + Spring Boot 3.5
- Sa-Token（Redis Session + `Authorization: Bearer` Header Token）
- MyBatis-Plus + MySQL 8.0 + HikariCP
- Redis / Redisson
- Flyway 数据库迁移
- Knife4j（OpenAPI 文档）
- EasyExcel、MinIO/S3、ip2region

### 前端

- Vben Admin v5（pnpm monorepo，位于 `frontend-vben/`）
- 业务应用：`frontend-vben/apps/web-ele`（Vue 3 + TypeScript + Vite + Element Plus）
- 后端菜单驱动动态路由（`accessMode: backend`），权限指令 `v-access:code`
- 权限码常量集中于 `src/constants/permissions.ts`（与后端 `PermissionCodes.java` 对齐）
- CRUD 列表页范式：`useCrudGrid`（vxe-grid 封装，见 `src/composables/CRUD_MIGRATION.md`），语言策略为中文单语

## 后端模块结构

代码根：`src/main/java/com/enterprise/auth/platform`，按 `interfaces / application / domain / infrastructure` 四层组织，模块间通过 Facade 协作，边界由 `ModuleBoundaryTest`（ArchUnit）守护。

| 分类 | 模块 |
| --- | --- |
| 认证与安全 | `auth`（登录/会话/验证码/注册）、`security`（平台与租户安全策略） |
| 组织与权限 | `user`、`role`、`dept`、`menu`、`catalog`（角色/部门/租户目录聚合） |
| 多租户 | `tenant`（租户、套餐、能力覆盖） |
| 系统管理 | `system`（字典/参数/公告/分类）、`notification`（站内通知 + SSE）、`file`（MinIO/S3/本地存储 + 存储健康检查） |
| 观测与日志 | `log`（操作日志/登录日志 + IP 归属地）、`dashboard`（运行总览统计，`DashboardMetrics` 领域模型） |
| 效率工具 | `codegen`（代码生成：domain 渲染器 + JDBC 元数据提取 + ZIP/落盘）、`workflow`（轻量审批流：定义/实例/任务三服务 + 驳回策略状态机） |

横向支撑：

- `common/`：统一响应 `ApiResponse`、全局异常、租户/时区上下文（`TenantContextSupport`）、分页归一（`PaginationSupport`）、数据权限（DataScope）、限流拦截、缓存名称登记
- `infrastructure/`：Sa-Token 集成、MyBatis-Plus 配置、Redisson、第三方集成（MinIO、邮件、IP 定位）

## 可观测性

- Actuator：`/actuator/health`（含 DB、Redis、fileStorage 组件检查）、`/actuator/prometheus`（Micrometer 指标，带 `application` 标签）；dev/local 额外暴露 `metrics` 并显示健康明细，prod 收敛为 `health,info,prometheus`
- 结构化日志：`logback-spring.xml` —— 非 prod 环境保持 Spring Boot 控制台可读输出，prod 输出 JSON（logstash-logback-encoder，含 MDC 透传）
- 注意：`/actuator/**` 不在 Sa-Token 拦截范围（只拦 `/api/**`），生产环境需靠网络层保护

## 核心能力

- 账号密码登录、滑块验证码、登录风控、会话管理与强制下线
- RBAC + 菜单权限 + 按钮权限码，权限快照恢复与前端动态菜单
- 多租户隔离、租户上下文透传、平台管理员跨租户操作、租户套餐与能力覆盖
- 操作日志/登录日志、CSV 导出（含公式注入防护）、IP 归属地解析
- 站内通知（SSE 实时推送）、公告（富文本）、邮件通道配置
- 文件上传/下载/公开访问，MinIO/S3 与本地存储切换
- 轻量工作流：流程定义、发起/待办/已办、驳回策略（END/PREVIOUS/RESTART）、转签、催办
- 代码生成：表元数据 → 预览/落盘/ZIP 下载，可选自动注册菜单与权限

## API 文档

后端接口文档由 Knife4j 提供，本地启动后访问 `http://localhost:8080/doc.html`（生产环境默认关闭）。README 不再维护接口清单，以运行时文档为准。

## 快速启动

### 后端

首次启动前先准备 MySQL 与 Redis；应用启动时会自动执行 `src/main/resources/db/migration` 下的 Flyway 迁移（`V1__baseline.sql` 基线 + 增量脚本）。

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

默认端口：`8080`

连接信息通过环境变量注入（也可复制 `application-local.example.yml` 为 `application-local.yml` 本地覆盖，已加入 `.gitignore`）：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/enterprise_auth_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='<your-local-password>'
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='6379'
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

### 前端

```bash
cd frontend-vben
pnpm install
pnpm dev:ele
```

默认端口：`5777`（`apps/web-ele/.env.development` 中 `VITE_PORT`），开发代理指向后端 `/api`。

## 配置与环境

- `application.yml`：基线配置，不含 DB/Redis 明文，必须通过环境变量注入
- `application-dev.yml` / `application-staging.yml` / `application-prod.yml`：按环境覆盖（`DEV_*` / `STAGING_*` 前缀环境变量）
- 常用环境变量：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`APP_CORS_ALLOWED_ORIGIN`、`APP_SECURITY_SESSION_IDLE_SECONDS`、`APP_SECURITY_MAX_LOGIN_COUNT`、`FLYWAY_ENABLED`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`

## 构建与测试

### 后端

```bash
mvn "-Dmaven.repo.local=.m2repo" verify
```

后端测试依赖可连接的 MySQL 与 Redis，可用 `TEST_*` 环境变量覆盖测试库连接（见 `src/test/resources/application.yml`）。

### 前端

```bash
cd frontend-vben
pnpm check:type   # 类型检查
pnpm build:ele    # 构建 web-ele
pnpm lint         # 代码检查
```

## CI

`.github/workflows/ci.yml`：

- `backend`：MySQL/Redis service 容器 + `mvn verify`（含 ArchUnit 模块边界测试与 JaCoCo 报告）
- `frontend`：pnpm 安装 + `check:type` + `build:ele`

## 认证与会话模型（摘要）

1. 前端 `POST /api/auth/login`（租户 + 账号密码 + 滑块验证码）
2. 后端创建 Sa-Token Redis Session，返回 Bearer token
3. 前端持 `Authorization: Bearer <token>` 调用 `GET /api/auth/me` 恢复用户、菜单和权限快照
4. 改密、禁用、强制下线通过会话版本与 Redis Session 实现

约束：主链路不依赖 Cookie、不启用 CSRF Token、无自建 refresh token；浏览器只持有 Sa-Token 的 Bearer token。

## 后续方向

- 前端：剩余 11 组 CRUD 页面迁移到 `useCrudGrid` 范式（见 `CRUD_MIGRATION.md`）；在线用户权限码已同后端对齐为 `upms:session:kick`
- 后端：幂等注解框架（@Idempotent + Redis）；codegen 两套类型映射（生成/导入链路）统一
- 可观测性：链路追踪（Micrometer Tracing）、业务指标埋点（@Timed 等）按需接入
- 运维：监控接入、配置中心、网关与分布式任务能力按需启用（`future-components` profile 预留）
- 测试：已修复硬编码 id 与 `sys_audit_log` 遗留引用问题；集成测试（带 `@Tag("integration")`）需依赖外部或本地真实中间件环境，纯单元测试可直接通过 `mvn test -Punit-tests` 执行验证
