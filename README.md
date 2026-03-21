# 企业级权限管理平台

当前项目已进入“数据库实跑 + 认证中心可用 + 前后端联调 + 控制台体验持续优化”阶段。

## 技术栈

### 后端
- Spring Boot 3.2
- Spring Security
- Spring Authorization Server
- MyBatis-Plus
- MySQL 8.0
- Redis / Redisson

### 前端
- Vue 3 + TypeScript + Vite
- Element Plus + Pinia + Vue Router + Axios
- Sass + ECharts

---

## 当前完成范围

### 后端能力
- 认证中心可用：`/oauth2/authorize`、`/oauth2/token`、`/oauth2/jwks` 等
- OAuth2 客户端管理（增删改查、启停、轮换密钥、状态历史）
- OAuth2 作用域管理
- 授权记录管理（`/api/auth/consents`）
- RBAC + 数据权限链路可用
- 多租户拦截可用（含 SAS 授权表忽略配置修复，避免 consents 查询 500）
- 审计与导出任务治理（进度、重试、归档、批量归档、批量清理、保留策略）
- 租户套餐/能力与能力覆盖、变更历史

### 前端能力
- 登录回调、权限快照、菜单与页面联通
- 路由稳定性优化：
  - 动态路由改为静态注册 + 权限校验
  - 修复 `/system/consents` no-match
- 会话与鉴权体验优化：
  - 401/403 与刷新失败统一回登录页
  - 提示统一顶部展示
  - 退出改为后端注销 + 前端清会话 + 跳登录
- 控制台视觉升级：
  - 顶部固定头稳定化
  - 首页仪表盘重构（渐进加载、信息密度提升）
  - 非首页统一设计（筛选区、表格、抽屉、弹窗、详情区）

---

## 本轮计划执行结果（已完成）

### 1) 抽屉详情模板统一
- 接入统一抽屉区块模板（概览/说明/历史）：
  - `UsersView` 详情抽屉
  - `RolesView` 详情抽屉
  - `OAuthScopesView` 详情抽屉
  - 之前已接入：`OAuthClientsView`、`TenantsView`、`AuditView`
- 统一抽屉内部结构类：
  - `drawer-section`
  - `drawer-section--overview`
  - `drawer-section--scopes`
  - `drawer-section--guide`
  - `drawer-section--history`

### 2) 弹窗/抽屉表单交互统一
- 必填标识颜色、错误提示样式统一
- 输入焦点态、开关选中态统一
- 底部按钮区统一（右对齐、间距、按钮最小宽度）

### 3) 表格交互统一
- 表头、行密度、hover 行反馈统一
- 固定列阴影与分隔线统一
- 加载态遮罩与空态展示统一

### 4) 移动端管理页可用性增强
- 工具栏表单改为窄屏全宽布局
- 面板动作区支持换行，避免按钮挤压
- 对话框宽度与抽屉窄屏表现优化

---

## 主要接口

### OAuth2 作用域
- `GET /api/oauth-scopes`
- `POST /api/oauth-scopes`
- `PUT /api/oauth-scopes/{id}`
- `DELETE /api/oauth-scopes/{id}`

### OAuth2 客户端
- `GET /api/oauth-clients`
- `GET /api/oauth-clients/{id}`
- `POST /api/oauth-clients`
- `PUT /api/oauth-clients/{id}`
- `PUT /api/oauth-clients/{id}/status`
- `POST /api/oauth-clients/{id}/rotate-secret`
- `DELETE /api/oauth-clients/{id}`

### 授权记录
- `GET /api/auth/consents`
- `DELETE /api/auth/consents`

### 租户套餐与能力
- `GET /api/tenant-catalog/packages`
- `POST /api/tenant-catalog/packages`
- `PUT /api/tenant-catalog/packages/{id}`
- `DELETE /api/tenant-catalog/packages/{id}`
- `GET /api/tenant-catalog/capabilities`
- `POST /api/tenant-catalog/capabilities`
- `PUT /api/tenant-catalog/capabilities/{id}`
- `DELETE /api/tenant-catalog/capabilities/{id}`

### 审计与导出
- `GET /api/audit/events`
- `GET /api/audit/events/export`
- `POST /api/audit/exports`
- `GET /api/audit/exports`
- `POST /api/audit/exports/{taskId}/retry`
- `POST /api/audit/exports/{taskId}/archive`
- `POST /api/audit/exports/archive`
- `DELETE /api/audit/exports/{taskId}`
- `DELETE /api/audit/exports`
- `GET /api/audit/exports/policy`
- `PUT /api/audit/exports/policy`

---

## 启动方式

### 后端
```bash
mvn spring-boot:run
```

或：
```bash
mvn clean package
java -jar target/enterprise-auth-platform-0.0.1-SNAPSHOT.jar
```

默认端口：`8080`

### 前端
```bash
cd frontend
npm install
npm run dev
```

默认端口：`5173`

---

## 数据库初始化

- 主脚本：[enterprise_auth_platform.sql](./src/main/resources/database/enterprise_auth_platform.sql)
- 升级脚本：
  - [upgrade_20260320_domain_tables.sql](./src/main/resources/database/upgrade_20260320_domain_tables.sql)
  - [upgrade_20260320_system_indexes.sql](./src/main/resources/database/upgrade_20260320_system_indexes.sql)

默认连接：
- `jdbc:mysql://127.0.0.1:3306/enterprise_auth_platform`
- 用户名：`root`
- 密码：`123456`

---

## 构建与检查

### 后端
```bash
mvn "-Dmaven.repo.local=.m2repo" test
```

### 前端
```bash
cd frontend
npm run lint
npm run build
```

---

## 下一步建议

### 前端
- 继续把剩余抽屉页面按统一模板接入到 100%
- 针对复杂表格页补“列显隐/列宽记忆/密度切换”
- 增加关键页面端到端回归脚本（登录、鉴权失效、导出流程）

### 后端
- 细化租户套餐/能力变更影响分析
- 增强 OAuth2 作用域与客户端详情联动引导
- 扩展审计导出归档后的自动化治理策略

## 说明
- 本阶段多数为前端交互与设计一致性升级，不要求后端联动改造。
- 仅在新增字段、排序/筛选能力、导出字段扩展时需要后端配合。

## 2026-03-21 续做进度（第四批）

### 已完成
- 视觉基线新增移动端断点快照（390x844），与桌面端快照并行维护。
- 视觉基线测试重构为双端覆盖：
  - 菜单与主要管理页切换（桌面 + 移动）
  - 系统管理工作台及四个子页（桌面 + 移动）
- 新增 CI 工作流：`/.github/workflows/frontend-visual-regression.yml`
  - 触发：PR + 手动触发
  - 执行：`npm run test:visual`
  - 失败时上传测试产物，支持 PR 自动比对视觉回归。

### 系统管理四页统一空态/错误态规范
- 已补齐以下页面的统一处理（加载失败提示 + 重试、空列表占位）：
  - `SystemDictsView`
  - `SystemConfigsView`
  - `SystemNoticesView`
  - `SystemCategoriesView`
- 四页同时保持统一列表规范：列显隐 / 列宽记忆 / 密度切换 / 恢复默认。

### 关于 frontend/e2e 图片与 git 污染
- `frontend/e2e/visual-baseline.spec.ts-snapshots/*.png` 是视觉回归“基线图”，有实际用途，需保留在仓库中用于 CI 对比。
- 已在 `frontend/.gitignore` 增加约束：
  - 忽略 e2e 下其它临时 PNG
  - 仅保留 `*-desktop-*` 与 `*-mobile-*` 的基线图纳入版本管理
- `playwright-report`、`test-results`、`blob-report` 仍保持忽略，避免测试临时文件污染。

### 验证
- `cd frontend && npm run build` 通过
- `cd frontend && npm run test:visual:update` 通过
- `cd frontend && npx playwright test` 通过（6/6）
### 说明补充（视觉快照入库策略）
- 已按团队偏好调整为：`frontend/e2e/visual-baseline.spec.ts-snapshots/` 不纳入 git。
- CI 中改为执行 `npm run test:visual:update` 动态生成快照，并将快照作为 artifact 上传供 PR 审阅。
- 该策略可避免仓库持续提交图片文件；若未来需要“严格像素比对阻断合并”，需恢复基线图入库。
