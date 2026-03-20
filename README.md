# 企业级权限管理平台

当前仓库已经进入“数据库为主、前后端联调中”的阶段。

后端采用 `Spring Boot 3.2 + Spring Security + Spring Authorization Server + MyBatis-Plus + MySQL 8.0`，前端采用 `Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router + Axios + Sass + ECharts`。  
系统当前已具备统一认证、OAuth2 客户端管理、RBAC、数据权限、多租户、审计、系统管理，以及前端控制台基础骨架。

## 当前状态

### 已完成

- 后端单体模块化骨架已完成：`auth / user / role / permission / dept / tenant / audit / system`
- MySQL 已作为默认且唯一真实数据源
- Spring Authorization Server 已接入并可运行
- 标准端点已可用：
  - `/.well-known/openid-configuration`
  - `/oauth2/authorize`
  - `/oauth2/token`
  - `/oauth2/jwks`
  - `/login`
  - `/oauth2/consent`
- OAuth2 客户端已改为数据库驱动，来源表：`sys_oauth_client`
- 已支持中文登录页、中文同意页、多租户登录页
- 已支持 OAuth2 客户端管理接口
- 已支持 JWT 会话、刷新令牌、在线会话、强制下线
- 已支持 RBAC 与数据权限模型
- 数据权限已下沉到首期核心链路：
  - 用户查询与写入
  - 部门查询与写入
  - 审计查询
  - 系统管理中的字典、参数、公告查询与目标校验
- 已支持多租户上下文透传与 MyBatis-Plus 多租户拦截
- 已支持审计落库、分页、按条件筛选
- 已完成用户、角色、权限、部门、租户、字典、参数、公告基础 CRUD
- 前端管理台骨架已创建在 `frontend/`
- 前端已联调页面：
  - 登录页
  - OAuth2 回调页
  - 运行总览
  - OAuth2 客户端管理
  - 用户管理
  - 角色管理
  - 权限管理
  - 部门管理
  - 租户管理
  - 安全审计
  - 系统管理

### 当前验证结果

- 后端测试通过：

```bash
mvn "-Dmaven.repo.local=.m2repo" test
```

- 当前结果：
  - `40` 个测试通过
  - `4` 个测试因无 Docker 自动跳过

- 前端构建通过：

```bash
cd frontend
npm run build
```

- 前端 lint 通过：

```bash
cd frontend
npm run lint
```

## 技术栈

### 后端

- Java 17
- Maven 3.9.x
- Spring Boot 3.2.x
- Spring Security 6.x
- Spring Authorization Server 1.2.x
- MyBatis-Plus 3.5.5
- MySQL 8.0
- Redis / Redisson
- Knife4j OpenAPI 3

### 前端

- Vue 3.4.x
- TypeScript 5.4.5
- Element Plus 2.4.x
- Pinia 2.1.x
- Vue Router 4.2.x
- Axios 1.6.x
- Vite 5.0.x
- Sass 1.69.x
- ECharts 5.4.x
- ESLint + Prettier

说明：
- 原计划中的 TypeScript 为 `5.3.x`
- 当前实际采用 `5.4.5`
- 这是为了和 `Vue 3.4` 的类型检查兼容更稳

## 仓库结构

```text
enterprise-auth-platform/
├─ src/main/java/com/enterprise/auth/platform
├─ src/main/resources
│  ├─ application.yml
│  └─ database/enterprise_auth_platform.sql
├─ src/test/java/com/enterprise/auth/platform
└─ frontend/
```

## 启动方式

### 后端启动

默认配置文件：
- [application.yml](/e:/Myproject/enterprise-auth-platform/src/main/resources/application.yml)

直接运行：

```bash
mvn spring-boot:run
```

或打包后运行：

```bash
mvn clean package
java -jar target/enterprise-auth-platform-0.0.1-SNAPSHOT.jar
```

默认端口：
- `8080`

### 前端启动

前端目录：
- [frontend](/e:/Myproject/enterprise-auth-platform/frontend)

开发启动：

```bash
cd frontend
npm install
npm run dev
```

默认端口：
- `5173`

前端默认联调后端地址：
- `http://127.0.0.1:8080`

## 数据库初始化

当前以单一初始化脚本为准：
- [enterprise_auth_platform.sql](/e:/Myproject/enterprise-auth-platform/src/main/resources/database/enterprise_auth_platform.sql)

默认数据库连接配置：
- 地址：`jdbc:mysql://127.0.0.1:3306/enterprise_auth_platform`
- 用户名：`root`
- 密码：`123456`

当前 JDBC 已包含：
- `createDatabaseIfNotExist=true`

因此：
- 如果 MySQL 账号有建库权限，通常不需要手动创建数据库
- 如果没有建库权限，请先手工创建空库，再执行初始化脚本

初始化示例：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456 < src/main/resources/database/enterprise_auth_platform.sql
```

初始化后可快速检查：

```sql
USE enterprise_auth_platform;

SELECT COUNT(*) AS tenant_cnt FROM sys_tenant;
SELECT COUNT(*) AS user_cnt FROM sys_user;
SELECT COUNT(*) AS role_cnt FROM sys_role;
SELECT COUNT(*) AS perm_cnt FROM sys_permission;
SELECT COUNT(*) AS oauth_client_cnt FROM sys_oauth_client;
```

## 认证与授权

### 1. 现有管理接口

- `GET /api/auth/captcha`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/sessions`
- `POST /api/auth/sessions/{sessionId}/offline`

### 2. 标准 OAuth2 / OIDC

当前系统已经提供最小可运行的 Spring Authorization Server：

- `/.well-known/openid-configuration`
- `/oauth2/authorize`
- `/oauth2/token`
- `/oauth2/jwks`
- `/login`
- `/oauth2/consent`

### 3. OAuth2 客户端

客户端统一从数据库表读取：
- `sys_oauth_client`

当前默认已存在两类客户端：

- `eap-web`
  - 用于后端管理端 / 测试链路
  - 支持 `authorization_code / refresh_token / client_credentials`

- `eap-frontend-spa`
  - 用于前端控制台
  - 公共客户端
  - 使用 `Authorization Code + PKCE`

## 前端当前联调范围

当前前端已直接联调以下后端接口：

- `/api/auth/me`
- `/oauth2/authorize`
- `/oauth2/token`
- `/api/oauth-clients`
- `/api/users`
- `/api/users/{userId}/roles`
- `/api/roles`
- `/api/roles/{roleId}/permissions`
- `/api/permissions`
- `/api/depts`
- `/api/tenants`
- `/api/audit/events`
- `/api/system/features`
- `/api/system/dicts`
- `/api/system/configs`
- `/api/system/notices`

## 数据权限当前边界

### 已下沉到组织级数据权限

- 用户列表
- 用户详情目标校验
- 用户新增、修改、删除
- 用户分配角色
- 部门列表
- 部门新增、修改、删除
- 指定部门负责人
- 审计分页查询
- 字典列表与目标校验
- 参数列表与目标校验
- 公告列表与目标校验
- 强制会话下线

### 当前保持租户级控制

- 角色管理
- 权限管理
- 租户管理
- OAuth2 客户端管理

### 原因

- 用户、部门、审计、系统管理中的部分对象与组织可见范围直接相关，必须走数据权限
- 角色、权限、OAuth2 客户端当前仍被视为租户级主数据，不按部门范围切分
- 租户管理属于平台级能力，不适合再套组织级数据权限

## 预留组件状态

以下组件当前保留 Maven 引入和开关配置，但不进入默认主链路：

- Gateway
- Nacos Discovery
- Nacos Config
- RocketMQ
- Seata
- XXL-Job
- Loki

对应开关在 [application.yml](/e:/Myproject/enterprise-auth-platform/src/main/resources/application.yml) 的 `app.features.*` 下。

## 当前未完善项

以下内容已经明确还没做完，后续需要继续推进。

### 后端未完善

- Spring Authorization Server 仍是“首个可用版本”，还缺：
  - 客户端密钥重置能力
  - scope 中文配置持久化
  - 授权记录 / 授权历史
  - 租户 branding 配置化，而不是当前在登录页内硬编码样式策略
  - 更完整的第三方接入场景

- OAuth2 客户端管理仍缺：
  - 客户端详情页
  - 客户端使用说明返回
  - 客户端密钥轮换
  - 客户端状态启停

- 数据权限仍未覆盖未来所有模块，后续还要继续下沉到：
  - 岗位 / 职务 / 组织扩展模块
  - 报表与统计查询
  - 导入导出记录
  - 任务中心与报表任务

- 系统管理仍缺更完整产品能力：
  - 字典分组
  - 参数分类
  - 公告状态流转
  - 更完整的服务监控整合

- 对象存储 MinIO 还未接入实际业务
- RocketMQ / Seata / XXL-Job / Loki 仍未启用实际链路
- Redis / Redisson 当前仍是开关化能力，未在默认链路启用

### 前端未完善

- 页面已具备基础 CRUD 骨架，但仍偏“联调台”，还不是完整产品 UI
- 用户、角色、权限、部门、租户页面还缺：
  - 更完善的表单校验
  - 分页
  - 搜索筛选
  - 详情抽屉 / 详情页
  - 删除前引用校验提示

- 角色权限分配目前是多选框方式，后续可升级为资源树/权限树
- 部门管理目前还是平铺表格，后续应补成树形结构
- 审计页面还缺时间区间筛选和多条件组合筛选
- 系统管理页面还缺组件开关说明和更细的分类视图
- 前端当前未接入菜单动态渲染到路由层，只是导航和页面并存，后续可进一步基于 `/api/auth/me` 的菜单快照动态生成

### 文档未完善

- README 这次已更新到当前状态，但后续新增模块后仍需同步
- 数据库脚本中的部分中文注释历史上有编码污染，虽然不影响运行，但后续建议统一清一次初始化脚本内容
- `application.yml` 中仍有个别中文字段显示不干净，建议后续统一清理配置注释与默认值文本

## 下一步任务清单

为防后续遗漏，建议按下面顺序继续推进。

### P1：继续做成“可交付管理台”

1. 完善前端页面交互
- 用户管理增加搜索、分页、详情、重置密码
- 角色管理增加权限树
- 部门管理改为树形结构
- 租户管理增加套餐/状态/到期提醒视图
- 审计页面增加时间范围筛选和条件筛选

2. 完善前端动态菜单
- 基于 `/api/auth/me` 返回的菜单快照动态控制路由可见性
- 按权限隐藏不可访问页面

3. 完善前端异常体验
- 表单级校验
- 更细的错误提示
- 加载态、空状态、无权限状态

### P2：继续深挖认证中心

1. 完善 OAuth2 客户端能力
- 客户端详情接口
- 客户端密钥轮换
- 客户端启停
- scope 描述配置化

2. 完善授权记录
- 记录谁在什么租户下给什么客户端授予了哪些 scope
- 提供查询接口和审计联动

3. 完善多租户授权页
- 租户品牌色配置化
- 租户 Logo / 文案配置化
- 客户端说明 / 风险提示配置化

### P3：继续扩大数据权限覆盖

1. 对未来组织类模块继续下沉数据权限
2. 对报表和统计查询加入数据范围限制
3. 对导入导出记录加入可见范围控制

### P4：继续启用预留组件

1. Redis / Redisson 默认链路接入
2. MinIO 接入实际文件上传
3. XXL-Job 接入任务调度
4. RocketMQ 接入异步审计或事件发布
5. Gateway / Nacos 最小启用验证

## 常用命令

后端测试：

```bash
mvn "-Dmaven.repo.local=.m2repo" test
```

后端启动：

```bash
mvn spring-boot:run
```

前端安装依赖：

```bash
cd frontend
npm install
```

前端开发启动：

```bash
cd frontend
npm run dev
```

前端构建：

```bash
cd frontend
npm run build
```

前端检查：

```bash
cd frontend
npm run lint
```
