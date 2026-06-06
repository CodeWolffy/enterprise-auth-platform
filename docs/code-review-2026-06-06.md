# 代码审查报告 — enterprise-auth-platform

**审查日期**：2026-06-06  
**审查范围**：全量仓库（342 个 Java 文件、19 个 Flyway 迁移、Vue 3 前端 109 文件、37 个测试文件）  
**当前分支**：`功能拓展`（领先 main 13 个提交）

---

## 审查范围概述

- **后端**：Spring Boot 3.5.11 + MyBatis-Plus 3.5.15 + Sa-Token 1.45.0 + Redisson 3.52.0
- **前端**：Vue 3.4.38 + Vite 8.0.1 + TypeScript 5.4.5 + Element Plus 2.4.4
- **业务模块**：14 个（auth、user、role、dept、tenant、resource、file、audit、dashboard、system、security、codegen、workflow、notification）
- **数据库**：MySQL 8.0 + Flyway 迁移（19 个迁移文件）
- **测试**：37 个后端测试 + 4 个 Playwright e2e + 2 个 Vitest 单元测试

---

## 关键发现（Critical）

### C1 — Bearer Token 明文存入审计日志

**文件**：
- `src/main/java/com/enterprise/auth/platform/modules/auth/application/LoginApplicationService.java` 第 126 行
- `src/main/java/com/enterprise/auth/platform/modules/audit/application/AuditPayloadRedactor.java` 第 12-21 行

**描述**：`LOGIN_SUCCESS` 审计事件以 `sessionId` 为键存储了原始 Bearer Token（`StpUtil.getTokenValue()`）。`AuditPayloadRedactor` 的敏感词列表中不包含 `sessionId`，导致 Token 明文写入 `sys_audit_log.payload_json`。任何拥有 `AUDIT_READ` 权限的用户可从审计日志提取有效会话令牌。

**严重度**：Critical | **置信度**：95

---

## 高危发现（High）

### H1 — 工作流 10 个端点缺少 @SaCheckPermission

**文件**：`src/main/java/com/enterprise/auth/platform/modules/workflow/interfaces/WorkflowController.java`  
**行号**：91、101、125、135、144、154、164、173、183、189

**描述**：待办/已办/审批/驳回/转签/催办端点无控制器层权限注解，仅依赖服务层业务检查。`PermissionCodes` 中已定义 `WORKFLOW_READ` 和 `WORKFLOW_WRITE`，但未在控制器层强制执行。

**严重度**：High | **置信度**：85

---

### H2 — 工作流启动 TOCTOU 竞态

**文件**：`src/main/java/com/enterprise/auth/platform/modules/workflow/application/WorkflowApplicationService.java` 第 166-180 行

**描述**：`startInstance()` 先调用 `existsBusinessKey()` 检查业务键唯一性，再执行 `insert()`。并发场景下可能双重插入，`DuplicateKeyException` 未被捕获，以原始 `DataAccessException` 暴露。

**严重度**：High | **置信度**：80

---

### H3 — 工作流待办全量加载到内存

**文件**：`src/main/java/com/enterprise/auth/platform/modules/workflow/application/WorkflowApplicationService.java` 第 432-450 行

**描述**：`todoTasks()` 先查询全部 PENDING 状态任务（无 LIMIT），在 Java 内存中通过 `isActionable()` 过滤候选人后再分页。任务量增长后将导致严重性能问题和 OOM 风险。

**严重度**：High | **置信度**：85

---

### H4 — 模块边界违规

**文件**：
- `src/main/java/com/enterprise/auth/platform/modules/auth/application/PasswordResetApplicationService.java`
- `src/main/java/com/enterprise/auth/platform/modules/dashboard/application/DashboardStatsService.java`
- `src/main/java/com/enterprise/auth/platform/modules/codegen/application/CodegenResourceRegistrationService.java`

**描述**：application 层直接导入其他模块的 mapper（`SysUserMapper`、5 个 dashboard 用 mapper、`SysResourceMapper`、`SysRoleMapper`），违反 ArchUnit 模块边界规则。

**严重度**：High | **置信度**：95

---

### H5 — 模块间循环依赖

**涉及模块对**：
- `resource ↔ tenant`：`ResourceService` → tenant infrastructure → `TenantResourcePolicyFacade` → `ResourceService`
- `auth ↔ user`：`LoginApplicationService` → user application → `UserManagementService` → auth services
- `auth ↔ tenant`：`TenantSwitchApplicationService` → tenant facade → `TenantAccessPolicy` → auth services

**严重度**：High | **置信度**：85-90

---

### H6 — 巨型服务类

| 服务 | 行数 | 构造依赖 |
|------|------|----------|
| `CodegenApplicationService` | 1,113 | — |
| `WorkflowApplicationService` | 917 | 9 个 |
| `TenantManagementService` | 761 | 12 个 |

**严重度**：High | **置信度**：100

---

### H7 — application-local.yml 硬编码凭据已提交

**文件**：`src/main/resources/application-local.yml`

**描述**：`.`gitignore` 已有忽略条目，但该文件已被 Git 跟踪。包含 MySQL 密码 `123456`、MinIO access-key `16696734992`、MinIO secret-key `Ilove0416@`。

**严重度**：High | **置信度**：100

---

### H8 — common/ 和 infrastructure/ 包违反依赖方向

**涉及文件**（9 处）：
- `common/` → `modules/`：`DataScopeService`（直接导入 mapper）、`PasswordValidator`、`GlobalExceptionHandler`、`AuthContextHolder`、`PlatformAdminSupport`、`RateLimitSupport`
- `infrastructure/` → `modules/`：`SaTokenUserContextInterceptor`、`SaTokenPermissionProvider`、`MybatisPlusConfig`

**严重度**：High | **置信度**：90-95

---

### H9 — role 模块零测试

**文件**：`src/main/java/com/enterprise/auth/platform/modules/role/`（18 个 Java 源文件）

**描述**：角色 CRUD、资源分配、权限查询等核心路径，测试目录完全不存在。其他 13 个模块均有测试。

**严重度**：High | **置信度**：95

---

## 中危发现（Medium）

### M1 — CORS allowedHeaders("*") 与 allowCredentials(true) 冲突

**文件**：`src/main/java/com/enterprise/auth/platform/infrastructure/config/WebMvcConfig.java` 第 73 行

**描述**：`allowCredentials(true)` 时 `*` 通配符违反 CORS 规范，浏览器忽略通配符，削弱 Authorization 头防护。

**严重度**：Medium | **置信度**：90

---

### M2 — application/ → interfaces/ 反向依赖

**描述**：约 20 个 application 服务导入同模块 interfaces/ 包获取 DTO，破坏依赖倒置原则。

**严重度**：Medium | **置信度**：90

---

### M3 — infrastructure/ → application/ 反向依赖

**涉及文件**：`RoutingObjectStorageService`、`DatabaseUserRepository` 等 infrastructure 类导入 application 层抽象。

**严重度**：Medium | **置信度**：90

---

### M4 — dashboard、codegen 模块缺少分层

- `dashboard/`：缺少 `domain/` 和 `infrastructure/`
- `codegen/`：缺少 `domain/`

**严重度**：Medium | **置信度**：100

---

### M5 — 前端 e2e 测试 100% Mock API

**文件**：`frontend/e2e/`（4 个 Playwright 规范）

**描述**：全部使用 `page.route('**/*')` 拦截所有 API 请求返回硬编码数据，从未与真实后端交互。

**严重度**：Medium | **置信度**：95

---

### M6 — 前端单元测试覆盖极薄

**描述**：仅 2 个 Vitest 测试（`permissionSnapshot`、`useCrudList`），23 个视图组件、18 个 API 模块、5 个 store 绝大部分无测试。

**严重度**：Medium | **置信度**：90

---

### M7 — V1 基线迁移使用 DROP TABLE IF EXISTS

**文件**：`src/main/resources/db/migration/V1__baseline.sql`

**描述**：若在非空库中意外重执行导致数据丢失（仅在异常运维场景触发）。

**严重度**：Medium | **置信度**：85

---

### M8 — ResourceAuthorizationControllerTest 缺少 @BeforeEach 清理

**文件**：`src/test/java/com/enterprise/auth/platform/resource/ResourceAuthorizationControllerTest.java`

**描述**：仅 `@AfterEach` 清理，测试中途崩溃时残留数据影响后续运行。

**严重度**：Medium | **置信度**：85

---

## 低危发现（Low）

### L1 — 登录错误信息区分导致用户名枚举

**文件**：`src/main/java/com/enterprise/auth/platform/modules/auth/application/LoginApplicationService.java` 第 79-90、170-175 行

**描述**："账户已锁定"、"用户已禁用"、"剩余尝试次数：N" 三种不同响应可区分有效用户。

**严重度**：Low | **置信度**：90

---

### L2 — CodegenApplicationService 自调用绕过 @Transactional(readOnly=true)

**文件**：`src/main/java/com/enterprise/auth/platform/modules/codegen/application/CodegenApplicationService.java` 第 108-112 行

**描述**：`generate()`（读写事务）直接调用 `this.preview()`（标注只读事务），AOP 代理被绕过。

**严重度**：Low | **置信度**：80

---

### L3 — CodegenView.vue el-checkbox 冗余 value 属性

**文件**：`frontend/src/views/platform/CodegenView.vue` 约第 159 行

**描述**：`v-model` 数组模式下决定值的是 `:label`，`:value` 被忽略，造成维护困惑。

**严重度**：Low | **置信度**：90

---

## 已排除的误报

以下方面经确认安全：
- **SQL 注入**：全部使用 `#{}` 参数化查询，分页参数已归一化
- **文件上传**：magic bytes 校验 + 路径穿越防护（`target.startsWith(root)` 归一化后检查）
- **异常信息泄露**：统一返回 "服务器内部错误" + requestId，堆栈仅记录服务端日志
- **密码哈希**：BCrypt 默认 cost=10，PasswordValidator 强制复杂度要求

---

## 按类别汇总

| 类别 | Critical | High | Medium | Low |
|------|:--:|:--:|:--:|:--:|
| 安全 / 凭据 | 1 | 1 | 1 | 1 |
| Bug / 竞态 | — | 2 | — | 1 |
| 架构 / 依赖方向 | — | 4 | 3 | — |
| 测试 | — | 1 | 3 | — |
| 前端 | — | — | 1 | 1 |

---
*审查方式：多路并行代理审查（安全、Bug、架构、测试质量），合成后人工确认。*