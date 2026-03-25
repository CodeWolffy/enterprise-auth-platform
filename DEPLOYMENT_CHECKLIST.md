# 部署前检查清单（enterprise-auth-platform）

更新时间：2026-03-23

## 1. 运行环境

- [ ] Java 17 可用（`java -version`）
- [ ] MySQL 8.0 可连接
- [ ] Redis 可连接
- [ ] 应用部署主机时间与时区正确（建议 UTC+8 或与业务统一）

## 2. 数据库（DB）

- [ ] 数据库已创建：`enterprise_auth_platform`
- [ ] 应用账号具备最小必要权限（DDL/DML 按实际策略配置）
- [ ] `spring.datasource.url` 指向目标库（含字符集与时区参数）
- [ ] JDBC URL 已包含 `serverTimezone=UTC`
- [ ] `spring.datasource.username` / `spring.datasource.password` 已配置到部署环境
- [ ] 初始化脚本已执行（`src/main/resources/database/enterprise_auth_platform.sql`）
- [ ] 已执行用户名全局唯一迁移（历史库必做）：
	- `SELECT username, COUNT(*) cnt FROM sys_user WHERE deleted = 0 GROUP BY username HAVING cnt > 1;` 结果应为空
	- `ALTER TABLE sys_user DROP INDEX uk_sys_user_tenant_username;`
	- `ALTER TABLE sys_user ADD UNIQUE INDEX uk_sys_user_username (username);`
- [ ] 数据库时区已核对：`SHOW VARIABLES LIKE 'time_zone';`、`SHOW VARIABLES LIKE 'system_time_zone';`
- [ ] 查看数据库记录时，若需北京时间显示，已在会话执行：`SET time_zone = '+08:00';`
- [ ] 连接池参数符合环境容量（并发、连接数、超时）

## 3. Redis

- [ ] `spring.data.redis.host` / `port` / `database` 已配置正确
- [ ] 网络策略允许应用访问 Redis
- [ ] 若开启认证，已配置 Redis 密码
- [ ] `app.security.redis.session-enabled` 与部署目标一致
- [ ] `app.security.redis.redisson-enabled` 与部署目标一致
- [ ] `app.security.redis.key-prefix` 与其他系统不冲突

## 4. issuer（授权服务器标识）

- [ ] `app.authorization-server.issuer` 使用最终对外访问地址
- [ ] 生产环境必须是 HTTPS 地址
- [ ] 反向代理/网关场景下，对外域名与 issuer 完全一致
- [ ] 下游 OAuth2/OIDC 客户端已同步最新 issuer

## 5. allowed-origins（前端跨域来源）

- [ ] `app.frontend.allowed-origins` 仅保留实际前端域名
- [ ] 不包含 `null`、通配符 `*`、测试域名
- [ ] 协议/主机/端口与前端真实访问地址完全一致
- [ ] 如果有多个环境（staging/prod），分别核对各自配置

## 6. 回调地址（OAuth2 Redirect URIs）

- [ ] `app.frontend.redirect-uris` 与前端回调页面路径一致
- [ ] 所有回调地址均为白名单明确值，不使用通配符
- [ ] 生产环境回调地址均为 HTTPS
- [ ] OAuth2 客户端登记信息与该配置一致（包括端口和路径）

## 7. 租户参数

- [ ] `app.tenant.header-name` 与网关/前端透传头一致（默认 `X-Tenant-Id`）
- [ ] `app.tenant.platform-tenant-id` 与平台租户实际标识一致
- [ ] `app.tenant.mybatis-tenant-enabled` 与部署策略一致
- [ ] `app.tenant.mybatis-ignore-tables` 已核对（仅保留全局共享表）
- [ ] 网关或入口层已限制非法租户头注入

## 8. 启动与回归检查

- [ ] 后端启动成功（`/actuator/health` 返回 UP）
- [ ] 前端可正常登录并完成授权回调
- [ ] 关键接口冒烟通过：登录、刷新 token、用户查询、租户查询、审计查询
- [ ] 审计导出流程可创建任务并下载结果
- [ ] CORS 校验正常（允许前端域名，拒绝非法 Origin）

## 9. 发布后观测

- [ ] 应用日志无持续 ERROR/大量 WARN
- [ ] 认证失败率、接口 4xx/5xx 比例在预期范围
- [ ] Redis key 增长与过期行为正常
- [ ] 核心页面与关键 API 延迟在 SLO 范围内

## 10. 数据库客户端查看规范（推荐）

- [ ] 已准备运维 SQL 模板：`src/main/resources/database/timezone_check_and_view.sql`
- [ ] Navicat / DBeaver 连接已配置“会话初始化 SQL”：`SET time_zone = '+08:00';`
- [ ] 团队约定：应用写入与存储保持 UTC，不在数据库做全量 `+8h` 数据修正
