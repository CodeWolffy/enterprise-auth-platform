# 企业级权限管理平台

当前仓库为首期后端实现，采用 Spring Boot 3.2、Java 17、MyBatis-Plus、MySQL 8.0，按模块化单体方式建设。系统已具备基础认证、授权、多租户、审计与系统管理能力，数据库已经作为默认且唯一的数据来源。

## 技术基线

- Java 17
- Maven 3.9.x
- Spring Boot 3.2.5
- Spring Security 6.x
- Spring Authorization Server 1.2.4
- MyBatis-Plus 3.5.5
- MySQL 8.0
- Redis / Redisson
- Knife4j OpenAPI 3

## 启动方式

默认配置文件：
- [application.yml](/e:/Myproject/enterprise-auth-platform/src/main/resources/application.yml)

直接启动：

```bash
mvn spring-boot:run
```

打包后启动：

```bash
mvn clean package
java -jar target/enterprise-auth-platform-0.0.1-SNAPSHOT.jar
```

默认端口：
- `8080`

## 数据库初始化

当前项目以单一初始化脚本为准：
- [enterprise_auth_platform.sql](/e:/Myproject/enterprise-auth-platform/src/main/resources/database/enterprise_auth_platform.sql)

默认数据库连接：
- 地址：`jdbc:mysql://127.0.0.1:3306/enterprise_auth_platform`
- 用户名：`root`
- 密码：`123456`

当前 JDBC 连接串已包含 `createDatabaseIfNotExist=true`。如果当前 MySQL 账号具备建库权限，通常不需要手动创建数据库；如果没有建库权限，请先手工创建空库，再执行初始化脚本。

初始化示例：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456 < src/main/resources/database/enterprise_auth_platform.sql
```

初始化完成后可快速验证：

```sql
USE enterprise_auth_platform;
SELECT COUNT(*) AS tenant_cnt FROM sys_tenant;
SELECT COUNT(*) AS user_cnt FROM sys_user;
SELECT COUNT(*) AS role_cnt FROM sys_role;
SELECT COUNT(*) AS perm_cnt FROM sys_permission;
SELECT COUNT(*) AS oauth_client_cnt FROM sys_oauth_client;
```

## 认证基线

当前系统同时提供两套认证方式。

### 1. 管理端现有认证接口

- `GET /api/auth/captcha`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/sessions`
- `POST /api/auth/sessions/{sessionId}/offline`

这套接口仍然保留，适合当前管理端直接接入。

### 2. Spring Authorization Server 标准认证链路

系统已补齐最小可运行的 Spring Authorization Server，提供标准 OAuth2 / OIDC 端点：

- `/.well-known/openid-configuration`
- `/oauth2/authorize`
- `/oauth2/token`
- `/oauth2/jwks`
- `/login`

当前 OAuth2 客户端统一从数据库表 `sys_oauth_client` 读取，不再从 `application.yml` 回退读取。初始化脚本已经内置默认客户端：

- `client_id`：`eap-web`
- `client_secret`：`eap-web-secret`
- `grant_types`：`authorization_code,refresh_token,client_credentials`
- `scopes`：`openid,profile,api.read,api.write`

说明：

- 标准认证链路当前为最小可运行版本
- 已支持数据库客户端注册信息读取
- 已提供中文登录页，并支持通过 `tenantId` 进入租户登录流程
- 当前 OAuth2 浏览器授权流程仍以平台租户场景为主，多租户授权页仍可继续细化

## Redis / Redisson 开关

默认关闭 Redis 会话和验证码存储，配置位于 [application.yml](/e:/Myproject/enterprise-auth-platform/src/main/resources/application.yml)：

- `app.security.redis.session-enabled=false`
- `app.security.redis.captcha-enabled=false`
- `app.security.redis.redisson-enabled=false`

含义：

- `session-enabled=true`：启用 Redis 会话存储
- `captcha-enabled=true`：启用 Redis 验证码存储
- `redisson-enabled=true`：在启用 Redis 时优先使用 Redisson

示例：

```bash
mvn spring-boot:run "-Dapp.security.redis.session-enabled=true" "-Dapp.security.redis.captcha-enabled=true" "-Dapp.security.redis.redisson-enabled=true"
```

## 测试说明

当前测试基线按真实 MySQL 数据设计，不再按默认内存模式设计。

运行前提：

1. 已按 [enterprise_auth_platform.sql](/e:/Myproject/enterprise-auth-platform/src/main/resources/database/enterprise_auth_platform.sql) 初始化数据库
2. 本地 MySQL 连接信息与 [application.yml](/e:/Myproject/enterprise-auth-platform/src/main/resources/application.yml) 一致，或已通过环境变量/启动参数覆盖

运行命令：

```bash
mvn "-Dmaven.repo.local=.m2repo" test
```

说明：

- 业务测试默认直接走真实数据库
- OAuth2 客户端测试默认直接读取并维护 `sys_oauth_client`
- 部分 Testcontainers 相关测试在无 Docker 环境下会自动跳过
- 当前错误响应已统一为 `code + success + data + message`

## 已完成范围

### 核心能力

- 账号密码登录、验证码、刷新、登出、在线会话、强制下线
- Spring Authorization Server 最小可运行版本
- JWT 访问令牌与刷新令牌
- RBAC 基础模型
- 数据权限模型：`SELF / DEPT / DEPT_AND_CHILDREN / CUSTOM / ALL`
- 多租户上下文透传与 MyBatis-Plus 租户拦截
- 登录审计、操作审计、分页查询
- 用户、角色、权限、部门、租户、字典、参数、公告基础 CRUD

### 工程能力

- OpenAPI / Knife4j 基础接口文档
- 统一错误响应与全局异常处理
- MySQL 持久化模式
- Redis / Redisson 会话与验证码开关
- 数据库初始化脚本
- MySQL 与接口层回归测试

## 数据权限边界

当前第二轮数据权限下沉边界如下。

### 已按组织级数据权限控制

- 用户列表查询
- 用户详情目标校验
- 用户新增、修改、删除
- 用户分配角色
- 部门列表查询
- 部门新增、修改、删除
- 指定部门负责人
- 审计分页查询
- 强制会话下线

### 当前保持租户级控制

- 角色管理
- 权限管理
- 租户管理
- 字典管理
- 参数管理
- 公告管理

当前这样划分的原因：

- 用户、部门、审计直接与组织可见范围相关，必须纳入数据权限
- 角色、权限、字典、参数、公告当前属于租户级主数据，首期不再额外叠加部门级数据权限
- 租户管理本身属于平台级能力，不适合再套组织级范围

### 下一轮适合继续下沉的模块

- 岗位 / 职务 / 组织扩展模块
- 报表与统计类查询
- 导入导出任务
- 后续新增的组织树衍生查询

## 预留组件状态

以下组件当前仅保留 Maven 引入能力，不参与默认运行主链路：

- Gateway
- Nacos Discovery
- Nacos Config
- RocketMQ
- Seata
- XXL-Job
- Loki

## 当前未完成范围

- Spring Authorization Server 仍是最小可运行版本，尚未补齐同意页、客户端管理界面、更细粒度多租户授权页
- 数据权限尚未覆盖所有未来模块，只完成了首期核心业务链路
- 前端管理台尚未纳入当前仓库
- MinIO、消息队列、分布式事务、任务调度、日志采集尚未启用实际业务链路

## 后续建议顺序

1. 继续完善 Spring Authorization Server 的多租户授权体验和客户端管理能力
2. 为角色、权限、系统管理模块补更完整的接口文档和示例
3. 启动前端管理台联调
4. 根据业务需要启用 Redis、Gateway、Nacos、MQ 等预留组件
