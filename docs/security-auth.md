# Sa-Token 认证与会话策略

本项目认证链路已统一为 Sa-Token Header Token 模式，前端通过 `Authorization: Bearer <token>` 访问受保护接口。

## 登录链路

1. 前端调用 `GET /api/auth/captcha` 获取滑块验证码图片与 `captchaId`。
2. 前端调用 `POST /api/auth/captcha/verify` 校验滑块轨迹。
3. 前端调用 `POST /api/auth/login` 提交用户名、密码、`captchaId`、`captchaCode` 和设备信息。
4. 后端使用 `StpUtil.login(...)` 创建 Sa-Token 会话，返回 token、租户、过期时间和改密状态。
5. 如果返回 `passwordChangeRequired=true`，前端直接进入 `/account/profile`，只允许完成密码修改。
6. 如果会话状态正常，前端保存 token 到 Pinia 持久化状态，并调用 `GET /api/auth/me` 恢复权限快照、菜单和租户上下文。

登录、注册、验证码和注册选项接口属于公开接口，不要求 Bearer token。

## Header Token

前端 Axios 拦截器会在非公开认证接口上附加：

```http
Authorization: Bearer <token>
X-Tenant-Id: <currentTenantId>
```

后端 Sa-Token 配置：

```yaml
sa-token:
  token-name: Authorization
  token-prefix: Bearer
  is-read-header: true
  is-read-cookie: false
  is-read-body: false
```

因此当前主链路不依赖 Cookie，也不使用 CSRF Token。CSRF 防护通常用于 Cookie 自动携带凭据的模式；当前 Bearer token 由前端显式写入请求头，不会被浏览器在跨站请求中自动携带。

## 受限改密态

P0-B 引入受限改密态，用于强制改密和密码过期场景。

触发条件：

- `sys_user.must_change_password = 1`。
- `password_updated_at + 当前租户生效策略 password_expire_days` 已过期；`password_expire_days = 0` 表示不过期。

登录响应会返回：

```json
{
  "tenantId": "tenant-a",
  "token": "...",
  "expiresAt": 1710000000000,
  "passwordChangeRequired": true,
  "passwordChangeReason": "FORCE_CHANGE"
}
```

受限态规则：

- 前端只进入 `/account/profile`。
- 后端只放行 `POST /api/account/password/change`。
- 其他受保护接口返回 `PASSWORD_CHANGE_REQUIRED`。
- 修改密码成功后清除 `must_change_password`，更新 `password_updated_at`，并刷新当前 token session 的 `sessionVersion`。

账号自助接口不绑定权限码，登录即可访问；受限态下仅改密接口可访问。

## 会话超时

关键配置：

- `timeout`: token 总有效期，当前默认 7 天。
- `active-timeout`: 空闲超时，默认读取 `APP_SECURITY_SESSION_IDLE_SECONDS`，缺省为 7200 秒。
- `auto-renew`: 访问时自动续期。
- `max-login-count`: 单账号最大并发会话数，默认读取 `APP_SECURITY_MAX_LOGIN_COUNT`，缺省为 10。
- `is-concurrent: true` 与 `is-share: false`: 允许多端登录，但每次登录生成独立 token。

`SaTokenUserContextInterceptor` 会在已登录请求中更新 token session 的 `lastAccessAt`，用于在线设备展示和排序。

## CORS

开发环境允许前端从 Vite 地址访问后端，并放行常用请求头：

- `Authorization`
- `X-Tenant-Id`
- `Content-Type`
- `X-Request-Id`

预检请求 `OPTIONS` 不执行 `StpUtil.checkLogin()`，避免未携带 token 的 CORS preflight 被认证拦截。

生产环境应通过 `app.frontend.allowed-origins` 或环境变量 `APP_FRONTEND_ALLOWED_ORIGIN` 收敛允许来源。

## 下线与失效语义

`sys_user.session_version` 是账号级会话失效版本号。密码修改、密码重置、管理员重置密码等安全敏感动作会自增该版本；旧 token session 中的版本与用户当前版本不一致时，会被踢下线。

后端统一返回结构中的 `code` 会被前端用于登录页提示：

- `SESSION_OFFLINE`: token 被踢下线或被替换，前端提示“当前账号已被强制下线，请重新登录”。
- `SESSION_EXPIRED`: token 超时，前端提示“登录已过期，请重新登录”。
- `INVALID_TOKEN`: token 无效或 Bearer 前缀缺失，前端提示“登录凭证已失效，请重新登录”。
- `UNAUTHORIZED`: 未登录或无法识别登录态。

前端收到受保护接口的 `401` 后会清理本地登录态，并跳转：

```text
/login?redirect=<current-path>&authReason=<code>
```

## 在线设备管理

接口：

- `GET /api/auth/sessions?scope=own`: 查看自己的在线会话。
- `GET /api/auth/sessions?scope=all`: 查看可管理范围内的在线会话，要求 `session:write` 或平台超级管理员身份，否则自动退回自己的会话。
- `POST /api/auth/sessions/{sessionId}/offline`: 强制指定会话下线。

权限规则：

- 用户可以下线自己的其他会话。
- 管理其他用户会话需要 `session:write`，并且目标会话必须在当前租户/数据权限可见范围内。
- 平台超级管理员可以跨租户管理可见会话。

前端顶部“在线设备管理”使用 `scope=own`；系统菜单“在线用户”页面使用 `scope=all`。

## 权限模型

接口权限通过 Sa-Token 注解控制，后端统一引用 `common/authz/PermissionCodes` 作为登记入口，避免 Controller、前端菜单、初始化数据出现拼写分叉。

当前登记的权限码：

- `user:read` / `user:write`
- `role:read` / `role:write`
- `dept:read` / `dept:write`
- `tenant:read` / `tenant:write`
- `system:read` / `system:write`
- `audit:read` / `audit:write`
- `security:read` / `security:write`
- `session:write`

前端的 `v-permission` 只负责隐藏或禁用交互入口，后端接口权限仍是最终边界。
