# 菜单权限模型整改记录

日期：2026-06-06

## 背景

本次验收发现，当前菜单管理改造方向基本正确：已经从旧的 `sys_resource` 兼做菜单，推进到参考 `haorong-mall` 的 `sys_menu + sys_role_menu` 模型。但现状仍存在权限来源未收敛、菜单种子数据不一致、编辑父级丢失、能力菜单未进入运行链路等问题。

本文件用于记录后续整改目标、边界、实施顺序和验收标准。

## 总体目标

将菜单、页面入口、按钮/API 授权统一收敛到菜单权限模型：

```text
平台菜单模板 sys_menu
  -> 租户能力范围过滤
  -> 租户可见菜单
  -> 角色菜单授权 sys_role_menu
  -> 登录权限快照 menus + grants
  -> 前端侧边栏、动态路由、按钮权限
```

整改后，`sys_resource`、`sys_permission`、`sys_role_resource`、`sys_role_permission` 不再作为登录授权来源。

## 参考项目吸收点

参考 `haorong-mall` 的 UPMS 设计，但不照搬微服务拆分。

保留吸收点：

- 菜单独立承载：目录、菜单、按钮权限统一放在 `sys_menu`。
- 角色授权独立承载：角色和菜单节点关系统一放在 `sys_role_menu`。
- 登录态只消费当前用户角色对应菜单树和权限标识。
- 前端动态路由由后端菜单树决定可见范围，前端清单只作为组件白名单。

不采纳点：

- 不拆成独立 UPMS 服务。
- 不恢复用户直接绑定权限。
- 不允许后端 `component` 直接变成任意前端 import 路径。

## 整改事项

### 1. 废弃旧权限表

#### 当前问题

后端回归测试显示：`sys_role_permission -> sys_permission` 中的 `payload:extra` 已经不再进入用户权限集合。当前实现实际只从 `sys_role_menu -> sys_menu.grant_key` 解析权限。

#### 目标状态

旧权限表正式退役：

- `sys_permission` 不再参与登录权限快照。
- `sys_role_permission` 不再参与登录权限快照。
- `sys_resource` 不再参与菜单树和登录授权。
- `sys_role_resource` 不再参与角色授权主链路。

#### 实施范围

后端：

- 清理或标记废弃旧权限查询入口。
- `DatabaseUserRepository` 只通过 `RoleGrantQueryFacade -> MenuService` 得到授权键。
- 修正旧测试：不再期待 `sys_role_permission` 中的授权进入登录快照。
- 保留旧表时，只作为历史兼容数据源，不参与运行时。

数据库：

- 新增迁移脚本，把旧 `sys_permission/sys_role_permission` 数据转换为 `sys_menu` 的 API 节点和 `sys_role_menu` 授权。
- 转换完成后，运行时不再读取旧表。

验收标准：

- 登录快照 `grants` 全部来自 `sys_menu.grant_key`。
- 后端测试不再依赖 `sys_permission/sys_role_permission`。
- 角色授权接口只写 `sys_role_menu`。

### 2. 修复“菜单管理”种子数据与前端路由清单不一致

#### 当前问题

历史种子数据里菜单管理仍是旧资源入口：

```text
resource_key = resources
route_key = resources
path = /system/resources
component = ResourceManagementView
```

前端新路由清单已经变成：

```text
routeKey = menus
path = /system/menus
component = MenuManagementView
```

迁移脚本目前只是把旧数据原样搬到 `sys_menu`，导致授权树、菜单树和前端白名单不一致。

#### 目标状态

菜单管理入口统一为：

```text
resource_key = menus
route_key = menus
grant_key = system:read 或 system:write 按页面访问策略确定
path = /system/menus
component = MenuManagementView
menu_name = 菜单管理
```

#### 实施范围

数据库：

- 新增迁移脚本修正 `sys_menu` 中旧 `resources` 节点。
- 同步修正 `sys_role_menu` 授权关系，保证原来拥有旧菜单管理权限的角色继续能访问新菜单。
- 如仍保留 `sys_resource` 历史表，也同步更新或标记旧节点，避免二次迁移污染。

前端：

- `module-manifest.ts` 保留 `menus` 路由白名单。
- 删除或隐藏旧 `resources` 入口，避免双入口。

验收标准：

- 登录快照里存在 `routeKey = menus`。
- 侧边栏点击菜单管理进入 `/system/menus`。
- 不再出现 `/system/resources` 作为主入口。

### 3. 修复菜单编辑导致子节点变顶层

#### 当前问题

菜单编辑弹窗隐藏了父级字段，保存时 payload 不带 `parentId`，后端更新会按 `null` 写入，导致子节点被移动到顶层。

#### 目标状态

编辑菜单时父级关系必须稳定：

- 如果页面不允许编辑父级，则更新接口保留原父级。
- 如果页面允许编辑父级，则必须明确展示和提交 `parentId`。
- 系统菜单关键结构不可被误改。

#### 实施范围

前端：

- 编辑时给表单补齐 `parentId`。
- 更新 payload 明确携带当前 `parentId`。
- 可选：编辑态显示“上级节点”，系统节点限制修改。

后端：

- `MenuService.update` 对 `parentId` 做保守处理：未传值时保留原父级，明确传值时才移动。
- 系统节点增加结构保护：`parentId/menuType/resourceKey/routeKey/grantKey/path/component` 不允许误改。

验收标准：

- 编辑任意二级/三级菜单后，父子结构不变。
- 修改排序、名称、图标不会改变层级。
- 系统节点无法被移动到其他父级。

### 4. 能力驱动菜单进入运行链路

#### 当前问题

`sys_tenant_capability_resource_scope` 已经建表，但当前菜单快照仍主要是：

```text
platform 菜单模板 -> 角色授权 -> 登录菜单快照
```

能力范围没有参与最终菜单过滤。

#### 目标状态

参考 `haorong-mall` 的租户菜单约束思路，实现本项目的能力菜单链路：

```text
sys_menu 平台模板
  -> sys_tenant_capability_resource_scope 能力范围
  -> 租户已启用能力
  -> 租户最终可见菜单
  -> 角色已授权菜单
  -> 当前用户菜单树和 grants
```

#### 数据结构职责

`sys_menu`：平台菜单和权限模板。

`sys_tenant_capability_resource_scope`：能力与菜单资源的绑定范围。

- `VISIBLE`：控制该能力启用后租户可见哪些菜单节点。
- `GRANT`：控制该能力启用后角色可被授予哪些按钮/API 授权。
- `required`：表示该能力的基础节点，默认应自动纳入可见范围。

`sys_tenant_capability` / 租户套餐能力：决定租户启用了哪些能力。

`sys_role_menu`：角色在租户范围内实际拥有的菜单与权限节点。

#### 运行策略

平台超级管理员：

```text
读取完整 sys_menu 模板
```

普通租户用户：

```text
读取租户启用能力
  -> 找到能力绑定的 VISIBLE/GRANT 资源键
  -> 从 sys_menu 中取允许范围
  -> 角色授权只在允许范围内生效
  -> 菜单树只展示 DIR/MENU 且 visible/enabled 的节点
  -> grants 只输出允许范围内且已授权的 grant_key
```

角色授权页面：

```text
只能展示当前租户能力范围内可授予的菜单/按钮/API 节点
```

租户切换：

```text
切换 activeTenantId 后重新按目标租户能力计算菜单和 grants
```

#### 实施范围

后端：

- 新增能力菜单查询服务，封装租户能力到菜单范围的解析。
- `MenuService.resolveMenuTree` 接入租户能力过滤。
- `MenuService.resolveGrantKeys` 接入租户能力过滤。
- `RoleManagementService.assignMenus` 校验菜单 ID 必须在租户能力允许范围内。
- 租户切换和登录快照保持同一套计算链路。

前端：

- 角色授权弹窗展示的是“当前租户可授权菜单树”。
- 平台菜单管理仍展示平台模板树。
- 菜单保存后提示刷新权限快照，或主动重新拉取 `/api/auth/me`。

验收标准：

- 禁用某租户能力后，该能力对应菜单不出现在登录菜单树。
- 角色不能授权超出租户能力范围的菜单/API 节点。
- 平台管理员仍可看到完整平台模板。
- 租户切换后菜单树按目标租户重新计算。

### 5. 次要问题一并修复

#### 5.1 接口命名收敛

当前角色接口同时存在 `/resources` 与 `/menus`，页面文案仍混用“资源/菜单/权限”。

目标：

- 主链路使用 `/api/roles/{roleId}/menus`。
- `/resources` 标记废弃或仅保留兼容，不再被前端使用。
- 页面文案统一为“菜单权限”。

#### 5.2 权限快照刷新

当前角色授权、菜单编辑后，后端缓存有清理，但当前浏览器里的 `snapshot` 不会自动更新。

目标：

- 菜单管理保存后主动刷新当前用户权限快照，或明确提示需要刷新。
- 角色授权保存后，如影响当前用户，也刷新权限快照。

#### 5.3 测试补齐

新增重点测试：

- `/api/menus/tree` 返回平台模板菜单树。
- 新增/编辑菜单不破坏父子层级。
- `resources -> menus` 迁移后菜单管理入口可用。
- 角色授权只写 `sys_role_menu`。
- 登录快照只来自 `sys_menu + sys_role_menu`。
- 租户能力过滤能影响菜单树和 grants。

## 实施顺序建议

1. 数据口径统一：旧权限表退役，补历史数据迁移。
2. 菜单管理入口修正：`resources` 迁到 `menus`。
3. 编辑父级保护：前后端同时修。
4. 能力过滤接入运行链路。
5. 前端文案、接口命名、快照刷新和测试补齐。

## 风险点

- 旧权限表废弃会影响历史自定义权限，需要迁移到 `sys_menu` 的 API 节点。
- 菜单 ID 沿用旧 `sys_resource.id` 时，要避免和新增 `sys_menu` 自增 ID 冲突。
- 能力过滤如果只过滤菜单不校验角色授权写入，会出现“数据库有授权但运行时无效”的灰色状态。
- 前端组件白名单必须继续保留，不能因为后端有 `component` 就直接动态 import。

## 完成验收清单

- [ ] 后端测试通过，且不再依赖旧权限表作为授权来源。
- [ ] 前端构建通过。
- [ ] 登录后菜单管理入口为 `/system/menus`。
- [ ] 编辑子菜单后层级不变。
- [ ] 普通租户菜单受能力范围控制。
- [ ] 角色授权不能越过租户能力范围。
- [ ] 当前用户授权变更后可以刷新权限快照。
- [ ] `/resources` 旧接口不再被前端主流程调用。