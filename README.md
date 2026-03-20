# 企业级权限管理平台

当前仓库已经进入“数据库真实运行 + 前后端联调 + 认证中心产品化”的阶段。

后端基于 `Spring Boot 3.2 + Spring Security + Spring Authorization Server + MyBatis-Plus + MySQL 8.0 + Redis/Redisson`，前端基于 `Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router + Axios + Sass + ECharts`。

目前系统已经具备认证中心、OAuth2 客户端管理、RBAC、数据权限、多租户、审计、系统管理，以及前端管理台基础能力。

## 当前进度

### 已完成

- 后端采用模块化单体结构：
  - `auth`
  - `user`
  - `role`
  - `permission`
  - `dept`
  - `tenant`
  - `audit`
  - `system`
- MySQL 已作为当前唯一真实数据源
- Redis / Redisson 已接入
- Spring Authorization Server 已接入并可运行
- 标准 OAuth2 / OIDC 端点已可用：
  - `/.well-known/openid-configuration`
  - `/oauth2/authorize`
  - `/oauth2/token`
  - `/oauth2/jwks`
  - `/login`
  - `/oauth2/consent`
- OAuth2 客户端已改为数据库驱动，数据来源表：
  - `sys_oauth_client`
- 已支持中文登录页、中文同意页、多租户登录页
- 已支持 OAuth2 客户端管理能力：
  - 客户端列表
  - 客户端详情
  - 新增 / 编辑 / 删除
  - 启用 / 禁用
  - 密钥轮换
  - 接入说明展示
  - 授权记录联动
- 已支持自定义 JWT 会话、刷新令牌、在线会话、强制下线
- 已支持 RBAC + 数据权限模型
- 数据权限已下沉到首期核心链路：
  - 用户查询与写入
  - 部门查询与写入
  - 审计查询
  - 字典、参数、公告的查询与目标校验
  - 强制会话下线
- 已支持多租户上下文透传与 MyBatis-Plus 多租户拦截
- 已支持审计日志落库、分页、筛选
- 已完成用户、角色、权限、部门、租户、字典、参数、公告基础 CRUD
- `system` 模块已支持服务端分页、服务端筛选、服务端排序：
  - 字典：`createdAt / dictType / dictCode`
  - 参数：`createdAt / configKey / configName`
  - 公告：`publishTime / createdAt / noticeTitle`
- 前端管理台已创建在 `frontend/`
- 前端已联调页面：
  - 登录页
  - OAuth2 回调页
  - 控制台总览
  - OAuth2 客户端管理
  - 授权记录
  - 用户管理
  - 角色管理
  - 权限管理
  - 部门管理
  - 租户管理
  - 审计管理
  - 系统管理工作台
  - 字典管理
  - 参数管理
  - 公告管理

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
- Vite 8.0.x
- Sass
- ECharts 5.4.x
- ESLint + Prettier

说明：

- 原规划中的 TypeScript 为 `5.3.x`
- 当前实际采用 `5.4.5`
- 当前 Vite 已升级到 `8.x`，用于解决 Sass legacy JS API 弃用警告

## 仓库结构

```text
enterprise-auth-platform/
├─ src/main/java/com/enterprise/auth/platform
├─ src/main/resources
│  ├─ application.yml
│  ├─ database/enterprise_auth_platform.sql
│  ├─ database/upgrade_20260320_system_indexes.sql
│  └─ templates/
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

默认联调后端地址：

- `http://127.0.0.1:8080`

## 数据库初始化

当前以单一初始化脚本为准：

- [enterprise_auth_platform.sql](/e:/Myproject/enterprise-auth-platform/src/main/resources/database/enterprise_auth_platform.sql)

默认数据库连接配置：

- 地址：`jdbc:mysql://127.0.0.1:3306/enterprise_auth_platform`
- 用户名：`root`
- 密码：`123456`

JDBC 当前已包含：

- `createDatabaseIfNotExist=true`

因此：

- 如果 MySQL 账号有建库权限，通常不需要手动创建数据库
- 如果没有建库权限，请先手工创建空库，再执行初始化脚本

初始化示例：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456 < src/main/resources/database/enterprise_auth_platform.sql
```

索引升级脚本：

- [upgrade_20260320_system_indexes.sql](/e:/Myproject/enterprise-auth-platform/src/main/resources/database/upgrade_20260320_system_indexes.sql)

说明：

- 该脚本用于补充 `sys_dict / sys_config / sys_notice` 的分页筛选与排序索引
- 你当前已经手动执行完成，无需重复写入 README 的执行步骤

## 认证与授权

### 管理认证接口

- `GET /api/auth/captcha`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/auth/sessions`
- `POST /api/auth/sessions/{sessionId}/offline`

### 标准 OAuth2 / OIDC

当前系统已提供最小可运行的 Spring Authorization Server：

- `/.well-known/openid-configuration`
- `/oauth2/authorize`
- `/oauth2/token`
- `/oauth2/jwks`
- `/login`
- `/oauth2/consent`

### OAuth2 客户端

客户端统一从数据库表读取：

- `sys_oauth_client`

当前已使用的客户端包括：

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
- `/api/oauth-consents`
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

以下组件当前保留 Maven 引入和功能开关，但不进入默认主链路：

- Gateway
- Nacos Discovery
- Nacos Config
- RocketMQ
- Seata
- XXL-Job
- Loki

对应开关位于 [application.yml](/e:/Myproject/enterprise-auth-platform/src/main/resources/application.yml) 的 `app.features.*`。

## 当前仍待完善

### 后端

- Spring Authorization Server 已可用，但仍有产品化空间：
  - scope 中文描述配置化持久化
  - 更完整的客户端接入说明与第三方接入指引
  - 更细的多租户品牌配置能力
  - 授权记录与审计日志的更深联动
- OAuth2 客户端管理仍可继续增强：
  - 客户端使用说明回显优化
  - 客户端接入示例模板化
  - 客户端状态历史
  - 客户端授权记录联动增强
- 数据权限还未覆盖未来所有模块，后续还要继续下沉到：
  - 岗位 / 职务 / 组织扩展模块
  - 报表与统计查询
  - 导入导出记录
  - 任务中心与报表任务
- 系统管理仍缺更完整的产品能力：
  - 字典分组
  - 参数分类
  - 公告状态流转
  - 更完整的服务监控整合
- MinIO 还未接入真实业务
- RocketMQ / Seata / XXL-Job / Loki 仍未接入真实业务链路
- Redis / Redisson 虽已接入，但仍可继续统一成默认会话基础设施
- 仍有少量历史文件需要继续清理中文乱码，尤其是：
  - 个别旧页面
  - 个别配置注释
  - 少量历史测试文本
- [application.yml](/e:/Myproject/enterprise-auth-platform/src/main/resources/application.yml) 中仍可见个别中文配置值乱码，例如前端公共客户端名称，建议后续统一清理

### 前端

- 页面已经具备首版产品形态，但仍不是最终交付版本
- 用户、角色、权限、部门、租户页面仍可继续增强：
  - 更完整的详情页或详情抽屉
  - 更细的筛选条件
  - 更严格的表单校验
  - 删除前引用校验提示
- 角色权限分配当前仍是基础多选方式，后续建议升级为资源树 / 权限树
- 部门管理后续建议升级为真正的树形结构视图
- 租户管理后续建议补：
  - 套餐信息
  - 到期提醒
  - 状态历史
  - 可用能力清单
- 审计页面仍可继续增强：
  - 时间范围筛选
  - 多条件组合筛选
  - 导出
- 系统管理三页仍可继续增强：
  - 更多筛选项
  - 更强的详情展示
  - 分类视图
- 前端菜单当前仍以静态路由为主，后续可继续下沉为基于 `/api/auth/me` 返回菜单快照的动态路由生成
- 前端包体仍有继续优化空间：
  - `element-plus` 仍是最大包
  - `charts` chunk 仍较大

### 文档与配置

- README 后续新增模块后仍需同步
- 数据库初始化脚本中的中文注释仍建议后续统一再清理一轮
- `application.yml` 中若继续新增中文配置项，建议统一以 UTF-8 正常文本维护，避免再混入历史乱码

## 下一步建议

### P1：继续做成可交付管理台

1. 完善前端业务页交互
- 用户管理增加更多筛选、详情、重置密码、启停操作
- 角色管理升级权限树
- 部门管理升级树形视图
- 租户管理增加套餐、到期、能力视图
- 审计页面增加时间范围和组合筛选

2. 完善前端动态菜单
- 基于 `/api/auth/me` 返回的菜单快照生成路由与菜单
- 按权限隐藏不可访问页面

3. 完善前端异常体验
- 表单级校验
- 更细的错误提示
- 加载态、空状态、无权限状态

### P2：继续深挖认证中心

1. 完善 OAuth2 客户端能力
- 客户端使用说明模板化
- 客户端接入示例模板化
- 客户端状态历史
- scope 描述配置化

2. 完善授权记录
- 记录谁在什么租户下给什么客户端授予了哪些 scope
- 提供更细的查询接口和审计联动

3. 完善多租户授权页
- 租户品牌色配置化
- 租户 Logo / 文案配置化
- 客户端说明 / 风险提示配置化

### P3：继续扩大数据权限覆盖

1. 对未来组织类模块继续下沉数据权限
2. 对报表和统计查询加入数据范围控制
3. 对导入导出记录加入可见范围控制

### P4：继续启用预留组件

1. Redis / Redisson 进一步统一为默认会话基础设施
2. MinIO 接入实际文件上传
3. XXL-Job 接入任务调度
4. RocketMQ 接入异步审计或事件发布
5. Gateway / Nacos 做最小启用验证

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
