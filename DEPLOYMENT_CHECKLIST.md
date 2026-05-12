# 部署前检查清单（enterprise-auth-platform）

更新时间：2026-05-12

## 1. 运行环境

- [ ] Java 17 可用（`java -version`）
- [ ] Maven 可用（`mvn -version`）
- [ ] MySQL 8.0 可连接
- [ ] Redis 可连接
- [ ] 应用部署主机时间与时区正确（建议统一使用 UTC 存储，展示层转换）

## 2. 数据库

- [ ] 数据库已创建：`enterprise_auth_platform`
- [ ] 应用账号具备最小必要权限
- [ ] `DB_URL` 指向目标库，且包含字符集、时区和连接参数
- [ ] `DB_USERNAME` / `DB_PASSWORD` 已通过环境变量或部署平台密钥注入
- [ ] 初始化脚本已执行：`src/main/resources/database/enterprise_auth_platform.sql`
- [ ] 历史库已完成用户名全局唯一约束迁移：
  - `SELECT username, COUNT(*) cnt FROM sys_user WHERE deleted = 0 GROUP BY username HAVING cnt > 1;` 结果为空
  - `sys_user.username` 存在唯一索引
- [ ] 数据库时区已核对：`SHOW VARIABLES LIKE 'time_zone';`、`SHOW VARIABLES LIKE 'system_time_zone';`
- [ ] HikariCP 连接池参数符合部署容量

## 3. Redis 与会话

- [ ] `REDIS_HOST` / `REDIS_PORT` / `REDIS_DATABASE` 指向目标 Redis
- [ ] 如 Redis 开启认证，`REDIS_PASSWORD` 已通过环境变量或密钥系统注入
- [ ] 网络策略只允许应用访问 Redis，不暴露无关来源
- [ ] `app.security.redis.session-enabled` 与部署目标一致
- [ ] `app.security.redis.redisson-enabled` 与部署目标一致
- [ ] `app.security.redis.key-prefix` 和 `namespace-version` 与其他系统不冲突
- [ ] `APP_SECURITY_SESSION_IDLE_SECONDS` 和 `APP_SECURITY_MAX_LOGIN_COUNT` 已按环境设置

## 4. 前端跨域来源

- [ ] `app.cors.allowed-origins` 或 `APP_CORS_ALLOWED_ORIGIN` 仅配置真实前端域名
- [ ] 生产环境不包含 `null`、测试域名或无边界通配符
- [ ] 协议、主机、端口与真实访问地址一致
- [ ] 多环境分别核对 staging/prod 配置

## 5. Sa-Token Header Token 链路

- [ ] 登录接口 `POST /api/auth/login` 可返回 Bearer token
- [ ] 受保护接口请求头包含 `Authorization: Bearer <token>`
- [ ] 前端请求头包含正确的 `X-Tenant-Id`
- [ ] Cookie 不是主认证凭据，生产环境不依赖 CSRF Token 链路
- [ ] `GET /api/auth/me` 可恢复用户、菜单和权限快照
- [ ] 强制下线、过期、无效 token 的 401 语义与前端提示一致

## 6. 租户参数

- [ ] `app.tenant.header-name` 与前端/网关透传头一致，默认 `X-Tenant-Id`
- [ ] `app.tenant.platform-tenant-id` 与平台租户实际标识一致
- [ ] `app.tenant.mybatis-tenant-enabled` 与部署策略一致
- [ ] `app.tenant.mybatis-ignore-tables` 仅包含全局共享表
- [ ] 网关或入口层已限制非法租户头注入

## 7. 安全与观测

- [ ] `src/main/resources/application.yml` 与 `application-prod.yml` 未包含真实 DB/Redis 密码或公网 Redis 默认值
- [ ] 本地私有配置仅写入 `src/main/resources/application-local.yml`，且该文件已被 `.gitignore` 忽略
- [ ] 生产环境未提交真实 DB/Redis 密码到源码
- [ ] 生产环境 `allowed-origins` 已收敛
- [ ] `/actuator/health` 可访问且返回 UP
- [ ] 日志无持续 ERROR 或大量 WARN
- [ ] 认证失败率、接口 4xx/5xx 比例在预期范围
- [ ] Redis key 增长与过期行为正常
- [ ] 核心页面与关键 API 延迟在 SLO 范围内

## 8. 回归检查

- [ ] 登录、退出、会话恢复通过
- [ ] 在线设备查询与强制下线通过
- [ ] 用户、角色、部门、租户核心 CRUD 冒烟通过
- [ ] 资源授权与菜单渲染通过
- [ ] 审计查询与导出流程通过
- [ ] CORS 校验正常：允许合法前端来源，拒绝非法 Origin

## 9. 数据库客户端查看规范

- [ ] Navicat / DBeaver 连接已配置会话初始化 SQL：`SET time_zone = '+08:00';`
- [ ] 团队约定：应用写入与存储保持 UTC，不在数据库做全量 `+8h` 数据修正