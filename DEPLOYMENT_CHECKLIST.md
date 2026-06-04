# 部署前检查清单（enterprise-auth-platform）

更新时间：2026-06-04

## 1. 运行环境

- [ ] Java 17 可用（`java -version`）
- [ ] Maven 可用（`mvn -version`）
- [ ] MySQL 8.0 可连接
- [ ] Redis 可连接
- [ ] 应用部署主机时间与时区正确（建议统一使用 UTC 存储，展示层转换）

## 2. 数据库

- [ ] 数据库已创建：`enterprise_auth_platform`
- [ ] 应用账号具备最小必要权限，并覆盖本次发布所需的 DDL / INDEX 权限
- [ ] `DB_URL` 指向目标库，且包含字符集、时区和连接参数
- [ ] `DB_USERNAME` / `DB_PASSWORD` 已通过环境变量或部署平台密钥注入
- [ ] Flyway 已作为数据库初始化主路径，迁移目录为 `src/main/resources/db/migration`
- [ ] 全新环境首次启动前不再手工执行完整 SQL，应用首次启动会自动完成 Flyway 迁移
- [ ] 目标库如为已存在的非空历史库，已按上线方案完成 baseline：
  - 首次启动前启用 `FLYWAY_BASELINE_ON_MIGRATE=true` 或等效 Spring 配置
  - 启动后已确认写入 `flyway_schema_history`，且基线版本为 `1`
- [ ] `src/main/resources/database/enterprise_auth_platform.sql` 仅作为基线镜像，不作为部署主初始化路径
- [ ] 历史库已完成用户名全局唯一约束迁移：
  - `SELECT username, COUNT(*) cnt FROM sys_user WHERE deleted = 0 GROUP BY username HAVING cnt > 1;` 结果为空
  - `sys_user.username` 存在唯一索引
- [ ] 数据库时区已核对：`SHOW VARIABLES LIKE 'time_zone';`、`SHOW VARIABLES LIKE 'system_time_zone';`
- [ ] HikariCP 连接池参数符合部署容量
- [ ] P1-A 迁移已成功执行：`V202606030004__file_storage_minio.sql`、`V202606040001__p1a_dashboard_operation_logs.sql`
- [ ] `flyway_schema_history` 中 `202606040001` 为 `success=1`，不存在同版本失败记录
- [ ] `sys_resource` 已存在 `dashboard`、`operation-logs`、`api.dashboard.read`、`api.operation-log.read`、`api.operation-log.export`，且平台管理员已授权

## 3. Redis 与会话

- [ ] `REDIS_HOST` / `REDIS_PORT` / `REDIS_DATABASE` 指向目标 Redis
- [ ] 如 Redis 开启认证，`REDIS_PASSWORD` 已通过环境变量或密钥系统注入
- [ ] 网络策略只允许应用访问 Redis，不暴露无关来源
- [ ] `app.security.redis.session-enabled` 与部署目标一致
- [ ] `app.security.redis.redisson-enabled` 与部署目标一致
- [ ] `app.security.redis.key-prefix` 和 `namespace-version` 与其他系统不冲突
- [ ] `app.cache.key-prefix` 与 `app.cache.namespace-version` 已按环境隔离
- [ ] `app.cache.*-ttl` 已按业务环境确认，尤其是认证主体、注册策略、系统字典、参数、公告和分类缓存
- [ ] `APP_SECURITY_SESSION_IDLE_SECONDS` 和 `APP_SECURITY_MAX_LOGIN_COUNT` 已按环境设置

## 4. 文件存储与 MinIO

- [ ] `platform.file.storage` 已按环境选择 `local` 或 `minio`
- [ ] 使用 MinIO 时，`platform.minio.endpoint`、`access-key`、`secret-key`、`bucket`、`public-endpoint` 已通过环境变量或密钥系统注入
- [ ] MinIO bucket 可创建或已提前创建，应用账号具备上传、读取和删除对象的最小权限
- [ ] 公开文件访问路径 `/api/files/public/{fileKey}` 已通过网关和 CORS 验证
- [ ] 头像上传后 `avatarUrl` 可在浏览器直接展示，且重新登录后权限快照仍能恢复头像

## 5. 前端跨域来源

- [ ] `app.cors.allowed-origins` 或 `APP_CORS_ALLOWED_ORIGIN` 仅配置真实前端域名
- [ ] 生产环境不包含 `null`、测试域名或无边界通配符
- [ ] 协议、主机、端口与真实访问地址一致
- [ ] 多环境分别核对 staging/prod 配置

## 6. Sa-Token Header Token 链路

- [ ] 登录接口 `POST /api/auth/login` 可返回 Bearer token
- [ ] 受保护接口请求头包含 `Authorization: Bearer <token>`
- [ ] 前端请求头包含正确的 `X-Tenant-Id`
- [ ] Cookie 不是主认证凭据，生产环境不依赖 CSRF Token 链路
- [ ] `GET /api/auth/me` 可恢复用户、菜单和权限快照
- [ ] 强制下线、过期、无效 token 的 401 语义与前端提示一致

## 7. 租户参数

- [ ] `app.tenant.header-name` 与前端/网关透传头一致，默认 `X-Tenant-Id`
- [ ] `app.tenant.platform-tenant-id` 与平台租户实际标识一致
- [ ] `app.tenant.mybatis-tenant-enabled` 与部署策略一致
- [ ] `app.tenant.mybatis-ignore-tables` 仅包含全局共享表
- [ ] 网关或入口层已限制非法租户头注入

## 8. 安全与观测

- [ ] 已按环境选择 profile：`dev` / `staging` / `prod`，本机私有覆盖仅使用 `local`
- [ ] `application-dev.yml` 仅用于开发或开发部署，不承载预发/生产语义
- [ ] `application-staging.yml` 使用 `STAGING_*` 环境变量，不复用生产密钥
- [ ] `application-prod.yml` 使用生产密钥系统注入，默认关闭 Knife4j
- [ ] `src/main/resources/application.yml` 与 `application-prod.yml` 未包含真实 DB/Redis 密码或公网 Redis 默认值
- [ ] 本地私有配置仅写入 `src/main/resources/application-local.yml`，且该文件已被 `.gitignore` 忽略
- [ ] 生产环境未提交真实 DB/Redis 密码到源码
- [ ] 生产环境 `allowed-origins` 已收敛
- [ ] `/actuator/health` 可访问且返回 UP
- [ ] 日志无持续 ERROR 或大量 WARN
- [ ] 认证失败率、接口 4xx/5xx 比例在预期范围
- [ ] Redis key 增长与过期行为正常
- [ ] 核心页面与关键 API 延迟在 SLO 范围内

## 9. 回归检查

- [ ] 登录、退出、会话恢复通过
- [ ] 在线设备查询与强制下线通过
- [ ] 用户、角色、部门、租户核心 CRUD 冒烟通过
- [ ] 资源授权与菜单渲染通过
- [ ] 审计查询与导出流程通过
- [ ] P1-A：`/dashboard` 可展示真实统计数据
- [ ] P1-A：`/account/profile` 可上传头像，顶部用户头像可刷新展示
- [ ] P1-A：`/platform/files` 可分页查询、上传、下载、删除和复制公开链接
- [ ] P1-A：`/system/operation-logs` 可分页查询并导出 CSV，低权限账号无法导出
- [ ] CORS 校验正常：允许合法前端来源，拒绝非法 Origin

## 10. 数据库客户端查看规范

- [ ] Navicat / DBeaver 连接已配置会话初始化 SQL：`SET time_zone = '+08:00';`
- [ ] 团队约定：应用写入与存储保持 UTC，不在数据库做全量 `+8h` 数据修正