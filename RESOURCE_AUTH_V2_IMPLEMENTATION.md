# 资源授权 V2 实施文档

## 摘要
- 文档路径：`RESOURCE_AUTH_V2_IMPLEMENTATION.md`
- 实施顺序：后端核心先行 -> 接口联调 -> 前端接入与 E2E
- 切换策略：开发期一次性切换、清空重配、不保留旧权限链路

## 背景与目标
- 从静态菜单 + `permissions_json` 升级为统一资源授权模型（`DIR/MENU/BUTTON/API`）。
- 统一授权键 `grant_key`，格式为 `{module}:{action}[:sub]`，前端按钮与后端 API 共用同一键。
- 菜单展示、按钮可见、API 鉴权统一走资源授权结果。

## 数据模型
- `sys_resource`
  - 资源模板定义：树结构、资源类型、`route_key`、`grant_key`、`ancestors`、显示属性。
  - 由平台租户（`platform`）维护模板。
- `sys_role_resource`
  - 角色-资源关联，按租户存储授权结果。
- `sys_tenant_resource_override`
  - 租户对模板资源的覆盖：启停/显隐/排序/标题/图标。
- `sys_role`
  - 继续承载 `data_scope_type` 与 `data_scope_value_json`。
  - 不在本期把 data scope 下沉到 role-resource。

## 核心规则
- 角色分配资源时自动补齐祖先节点，防止导航断裂。
- 任一祖先禁用或不可见，子孙菜单自动不可见。
- 超级管理员走运行时全量授权逻辑，快照返回全量有效资源。
- 后端只下发 `route_key`，前端白名单映射组件，未知 `route_key` 丢弃并记录告警。

## API 变更
- 新增资源管理接口：
  - `GET /api/resources/tree`
  - `POST /api/resources`
  - `PUT /api/resources/{resourceId}`
  - `DELETE /api/resources/{resourceId}`
  - `PUT /api/resources/{resourceId}/sort`
- 新增角色资源分配接口：
  - `GET /api/roles/{roleId}/resources`
  - `PUT /api/roles/{roleId}/resources`
- 新增租户资源覆盖接口：
  - `GET /api/tenants/{tenantId}/resource-overrides`
  - `PUT /api/tenants/{tenantId}/resource-overrides`
- 调整：
  - `GET /api/auth/me` 返回 `menus`（树）+ `grants`（集合）
- 下线：
  - `/api/permissions`
  - `/api/roles/{roleId}/permissions`

## 初始化与迁移
- 主策略：清空重配。
- 基线初始化使用：`src/main/resources/database/enterprise_auth_platform.sql`。
- 增量兼容收口使用：`src/main/resources/database/migration/20260405_resource_auth_v2_residual_cleanup.sql`（仅旧库升级时执行）。
- 不再保留 `permissions_json` 兼容回填与 legacy 资源链路。

## 执行顺序（本次）
1. 执行 `src/main/resources/database/enterprise_auth_platform.sql`（或在空库执行业务基线脚本）。
2. 若是旧库升级场景，额外执行 `src/main/resources/database/migration/20260405_resource_auth_v2_residual_cleanup.sql`（空库可跳过）。
3. 启动后端，验证 `GET /api/resources/tree`、`GET /api/roles/{roleId}/resources`、`GET /api/auth/me`。
4. 启动前端，进入“系统管理 -> 菜单管理”，完成资源树维护与角色授权联调。
5. 做一次全链路验证：菜单可见性、按钮显隐、API 鉴权返回一致性（200/403）。

## 第一批任务（后端核心）
1. 建库与迁移脚本
   - 新增 3 张表、索引、初始模板资源。
   - 增加 `ancestors` 字段并提供祖先链能力。
2. 领域与持久层
   - 新增 Resource / RoleResource / TenantResourceOverride 实体与 Mapper。
3. 授权引擎与快照
   - 新增基于 `grant_key` 的统一授权解析。
   - `/api/auth/me` 改为 `menus + grants`。
4. 角色资源分配
   - 新增角色资源查询与分配接口。
   - 分配时自动补齐祖先资源。
5. 回归测试
   - 覆盖祖先链禁用、超管全量、跨租户隔离等核心场景。

## 假设
- 当前为开发环境，可直接切换。
- 数据权限仍按角色级模型生效。
- `grant_key` 是系统唯一授权语义，不再保留旧权限目录语义。
