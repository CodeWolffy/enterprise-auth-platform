# CRUD 列表迁移清单

本文是 `frontend-vben/apps/web-ele` 的真实列表页清单。通用列表使用 `useCrudGrid`，它统一处理搜索表单、分页、排序、响应归一化、删除确认和刷新。树形、双列表和编辑器页面保留专用 Grid 配置，不以机械迁移为目标。

## 状态定义

- **已迁移**：页面使用 `useCrudGrid`，查询接口返回 `records/list` 与 `total`；类型参数按页面成熟度逐步补齐。
- **保留专用配置**：页面存在树形、双列表、跨页面联动或代码生成编辑器等特殊交互，继续直接使用底层 `useVbenVxeGrid`。
- **待收敛**：页面已经具备列表能力，但仍需补充类型参数、真实后端分页或业务验收测试。

## 已迁移页面

以下页面已经使用 `useCrudGrid`：

- `src/views/upms/config/index.vue`：参数分页、分类/关键字查询、内置参数删除保护。
- `src/views/upms/dict/index.vue`：字典分页、缓存刷新和字典键值抽屉。
- `src/views/upms/dict-value/index.vue`：字典键值列表；接口暂不分页，因此关闭分页器。
- `src/views/upms/category/index.vue`：分类列表、关键字过滤、引用分析抽屉；数据量较小，保留前端过滤。
- `src/views/upms/file/index.vue`：文件列表、上传、下载和删除。
- `src/views/upms/log/index.vue`：操作日志列表和详情弹窗。
- `src/views/upms/login-log/index.vue`：登录日志列表。
- `src/views/upms/notice/index.vue`：公告分页、详情抽屉和删除。
- `src/views/upms/online-user/index.vue`：在线会话分页和强制下线。
- `src/views/upms/role/index.vue`：角色分页和菜单授权入口。
- `src/views/upms/tenant/index.vue`：租户分页和租户菜单入口。
- `src/views/upms/user/index.vue`：用户分页和用户详情入口。
- `src/views/gen/datasource/index.vue`：数据源列表；当前 API 返回全量列表，页面在统一 Grid 查询回调内完成搜索和分页。

## 保留专用配置的页面

以下页面直接使用底层 `useVbenVxeGrid`，每个页面的专用理由应在代码评审中保持可见：

- `src/views/upms/dept/index.vue`：部门树和树节点展开操作。
- `src/views/upms/menu/index.vue`：菜单树、拖拽/层级关系和树节点操作。
- `src/views/system/tenant-catalog/index.vue`：租户目录的套餐/菜单双列表联动。
- `src/views/gen/gen-table/index.vue`：数据源表与已导入表双列表，以及预览/生成代码操作。

工作流页面 `src/views/workflow/definitions.vue`、`done.vue`、`instances.vue` 和 `todo.vue` 目前包含任务操作、详情或状态流转，不纳入普通 CRUD 的机械迁移；后续可在保留专用操作列的前提下复用查询分页适配器。

## 当前待收敛项

1. 为已迁移页面的行数据、查询参数和主键补齐 `useCrudGrid<TRow, TQuery, TId>` 类型参数；接口文件中的 `any` 也应按业务 DTO 逐步替换。
2. 将数据源、分类和字典键值等全量接口升级为后端分页前，保持其页面级过滤和明确的最大数据量限制，避免把大数据集继续搬到浏览器。
3. 为在线会话、授权关系、工作流发起/审批和代码生成预览补充后端联调端到端用例；需要外部服务的用例通过环境变量启用，不以跳过代替通过。
4. 树形和双列表页面只在交互规则已经能映射到通用范式时迁移，不能为了减少底层 Grid 引用而牺牲操作语义。

## 页面验收条件

每个普通分页列表迁移完成后，至少验收以下行为：

1. 首次加载和刷新请求包含当前过滤条件、页码、页大小和稳定排序字段。
2. 后端返回 `records` 或 `list` 时，表格数据和 `total` 都能正确显示；空列表不会报错。
3. 切换页码、页大小和搜索条件后，列表不会使用上一页数据，搜索条件变化会回到第一页。
4. 删除操作先确认，成功后提示并刷新，取消不发删除请求，接口失败不会提示删除成功。
5. 行操作、按钮显示和接口请求继续受原有权限码控制。
6. 表单保存、授权、上传或状态变更后，列表能刷新且不会重复请求。
7. 类型检查、格式检查、单元测试和生产构建通过；具备后端环境时再执行对应端到端用例。
