# enterprise-auth-platform frontend

企业级权限管理平台前端，基于 Vue 3、TypeScript、Vite、Element Plus、Pinia 和 Vue Router 构建。

## 环境要求

- Node.js 20+
- npm 10+

## 常用命令

```bash
npm ci
npm run dev
npm run lint
npm run build
npm run test:e2e
npm run test:visual
```

## 本地开发

默认后端地址会根据当前前端访问主机推导为 `http://<host>:8080`。如需显式指定后端地址，设置：

```bash
VITE_BACKEND_ORIGIN=http://127.0.0.1:8080
```

## 目录约定

- `src/api/http.ts`：Axios 实例、认证头、租户头和错误处理。
- `src/api/modules/`：按业务域拆分的 API 模块，业务代码统一从 `@/api/modules` 导入。
- `src/views/`：路由页面，按业务域分组。
- `src/components/`：可复用组件。
- `src/stores/`：Pinia 状态。
- `src/types/`：前端共享类型。
- `e2e/`：Playwright E2E 与视觉回归用例。

## 视觉回归

- `npm run test:visual`：执行视觉快照对比，用于 CI。
- `npm run test:visual:update`：仅在需要生成或更新快照时执行。
- `e2e/**/*.png` 是视觉测试产物，不提交源码。
- `playwright-report/` 和 `test-results/` 是临时报告产物，不提交源码。
