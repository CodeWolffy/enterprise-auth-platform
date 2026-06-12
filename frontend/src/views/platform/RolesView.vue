<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">角色</span>
        <strong>{{ filteredRoles.length }}</strong>
        <span>当前筛选下的角色总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">全量范围</span>
        <strong>{{ allScopeCount }}</strong>
        <span>全量数据范围角色</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">部门范围</span>
        <strong>{{ deptScopeCount }}</strong>
        <span>部门级数据范围角色</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">自定义范围</span>
        <strong>{{ customScopeCount }}</strong>
        <span>自定义数据范围角色</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">角色</span>
          <h3>角色管理</h3>
        </div>
        <el-button v-permission="'upms:sysrole:add'" type="primary" @click="openRole()">新增角色</el-button>
      </div>

      <AdvancedSearch @search="handleSearch" @reset="resetSearch">
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="搜索角色名称、编码或描述" clearable />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="scopeFilter" placeholder="全部" clearable style="width: 180px">
            <el-option label="全部" value="" />
            <el-option label="全部数据" value="ALL" />
            <el-option label="仅本人" value="SELF" />
            <el-option label="本部门" value="DEPT" />
            <el-option label="本部门及子部门" value="DEPT_AND_CHILDREN" />
            <el-option label="自定义范围" value="CUSTOM" />
          </el-select>
        </el-form-item>
      </AdvancedSearch>

      <div class="table-tools">
        <el-radio-group v-model="roleTablePrefs.density" size="small">
          <el-radio-button value="compact">紧凑</el-radio-button>
          <el-radio-button value="default">默认</el-radio-button>
          <el-radio-button value="comfortable">宽松</el-radio-button>
        </el-radio-group>
        <el-popover placement="bottom-end" width="240" trigger="click">
          <template #reference>
            <el-button size="small">列显示</el-button>
          </template>
          <div class="column-chooser">
            <el-checkbox
              v-for="item in roleTablePrefs.columns"
              :key="item.key"
              :model-value="roleTablePrefs.visibleColumnMap[item.key]"
              @change="(value: boolean) => roleTablePrefs.setColumnVisible(item.key, value)"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </el-popover>
        <el-button size="small" @click="roleTablePrefs.reset()">恢复默认</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="pagedRoles"
        stripe
        :class="`table-density-${roleTablePrefs.density}`"
        @header-dragend="onRoleHeaderDragEnd"
      >
        <el-table-column
          v-if="roleTablePrefs.visibleColumnMap.code"
          column-key="code"
          prop="code"
          label="角色编码"
          min-width="140"
          :width="roleTablePrefs.getColumnWidth('code')"
        />
        <el-table-column
          v-if="roleTablePrefs.visibleColumnMap.name"
          column-key="name"
          prop="name"
          label="角色名称"
          min-width="160"
          :width="roleTablePrefs.getColumnWidth('name')"
        />
        <el-table-column
          v-if="roleTablePrefs.visibleColumnMap.description"
          column-key="description"
          prop="description"
          label="角色描述"
          min-width="220"
          show-overflow-tooltip
          :width="roleTablePrefs.getColumnWidth('description')"
        />
        <el-table-column
          v-if="roleTablePrefs.visibleColumnMap.dataScopeType"
          column-key="dataScopeType"
          prop="dataScopeType"
          label="数据范围"
          min-width="140"
          :width="roleTablePrefs.getColumnWidth('dataScopeType')"
        />
        <el-table-column
          v-if="roleTablePrefs.visibleColumnMap.menuCount"
          column-key="menuCount"
          label="权限数"
          min-width="100"
          :width="roleTablePrefs.getColumnWidth('menuCount')"
        >
          <template #default="{ row }">{{ assignedCountByRoleId[row.id] ?? 0 }}</template>
        </el-table-column>
        <el-table-column v-if="roleTablePrefs.visibleColumnMap.actions" column-key="actions" fixed="right" label="操作" width="320">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-permission="'upms:sysrole:edit'" link type="primary" @click="openRole(row)">编辑</el-button>
            <el-button v-permission="'upms:sysrole:edit'" link type="primary" @click="openMenuAssignment(row)">分配权限</el-button>
            <el-button v-permission="'upms:sysrole:del'" link type="danger" @click="removeRole(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无角色数据" />
        </template>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="filteredRoles.length"
          @size-change="handlePageChange"
          @current-change="handlePageChange"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="角色详情" size="680px">
      <template v-if="detailRole">
        <el-descriptions :column="2" border class="drawer-section drawer-section--overview">
          <el-descriptions-item label="角色编码">{{ detailRole.code }}</el-descriptions-item>
          <el-descriptions-item label="角色名称">{{ detailRole.name }}</el-descriptions-item>
          <el-descriptions-item label="数据范围">{{ detailRole.dataScopeType }}</el-descriptions-item>
          <el-descriptions-item label="自定义部门">{{ detailRole.customDeptIds?.length || 0 }}</el-descriptions-item>
          <el-descriptions-item label="已分配权限">{{ assignedMenuIds.length }}</el-descriptions-item>
          <el-descriptions-item label="角色描述" :span="2">{{ detailRole.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="detailRole.customDeptIds?.length" class="scope-tags drawer-section drawer-section--scopes">
          <el-tag v-for="deptId in detailRole.customDeptIds" :key="deptId" type="warning" effect="plain">
            部门 #{{ deptId }}
          </el-tag>
        </div>

        <div class="tree-panel drawer-section drawer-section--history">
          <div class="tree-panel__head">
            <strong>权限树</strong>
            <span>{{ assignedMenuIds.length }} 个菜单/权限节点</span>
          </div>
          <div class="menu-summary">
            <el-tag v-for="item in detailMenuSummary" :key="item.type" effect="plain">
              {{ typeLabel(item.type) }} / {{ item.count }}
            </el-tag>
          </div>
          <el-tree
            :data="detailMenuTree"
            node-key="id"
            default-expand-all
            :props="{ children: 'children', label: 'label' }"
            empty-text="暂无菜单"
          >
            <template #default="{ data }">
              <div class="menu-node">
                <span>{{ data.label }}</span>
                <el-tag size="small" effect="plain">{{ typeLabel(data.menuType) }}</el-tag>
              </div>
            </template>
          </el-tree>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="roleVisible" :title="editingRoleId ? '编辑角色' : '新增角色'" width="640px">
      <el-form ref="formRef" label-position="top" :model="roleForm" :rules="roleRules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="角色编码" prop="roleCode">
              <el-input v-model="roleForm.roleCode" :disabled="Boolean(editingRoleId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色名称" prop="roleName">
              <el-input v-model="roleForm.roleName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="角色描述">
          <el-input v-model="roleForm.roleDesc" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScopeType">
          <el-select v-model="roleForm.dataScopeType" style="width: 100%">
            <el-option label="全部数据" value="ALL" />
            <el-option label="仅本人" value="SELF" />
            <el-option label="本部门" value="DEPT" />
            <el-option label="本部门及子部门" value="DEPT_AND_CHILDREN" />
            <el-option label="自定义范围" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="roleForm.dataScopeType === 'CUSTOM'" label="自定义部门" prop="customDeptIds">
          <el-tree-select
            v-model="roleForm.customDeptIds"
            :data="departmentTree"
            multiple
            show-checkbox
            check-strictly
            node-key="id"
            :props="{ label: 'label', children: 'children' }"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button v-permission="['upms:sysrole:add', 'upms:sysrole:edit']" type="primary" @click="submitRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menuVisible" title="分配角色权限" width="800px">
      <div class="assignment-toolbar">
        <el-input v-model="menuKeyword" placeholder="筛选菜单名、编码或授权标识" clearable />
        <div class="assignment-toolbar__actions">
          <el-button @click="expandAll">全部展开</el-button>
          <el-button @click="collapseAll">全部折叠</el-button>
          <el-button :disabled="focusedMenuId == null" @click="selectFocusedDescendants">全选当前子级</el-button>
          <el-button @click="selectByTypes(['DIR', 'MENU'])">全选菜单模块</el-button>
          <el-button @click="selectByTypes(['BUTTON', 'API'])">全选操作权限</el-button>
          <el-button @click="clearMenuSelection">清空选择</el-button>
          <span class="assignment-toolbar__meta">共 {{ allMenuCount }} 个菜单/权限节点</span>
        </div>
        <div class="menu-summary">
          <el-tag v-for="item in selectedMenuSummary" :key="item.type" effect="plain">
            {{ typeLabel(item.type) }} / {{ item.count }}
          </el-tag>
        </div>
      </div>
      <el-tree
        ref="menuTreeRef"
        :data="filteredMenuTree"
        show-checkbox
        check-strictly
        highlight-current
        node-key="id"
        default-expand-all
        :props="{ children: 'children', label: 'label' }"
        class="permission-tree"
        @check="syncSelectedMenuIds"
        @node-click="handleMenuNodeClick"
      >
        <template #default="{ data }">
          <div class="menu-node">
            <span>{{ data.label }}</span>
            <div class="menu-node__meta">
              <el-tag size="small" effect="plain">{{ typeLabel(data.menuType) }}</el-tag>
              <el-tag v-if="data.grantKey" size="small" type="success" effect="plain">{{ data.grantKey }}</el-tag>
              <el-tag v-if="data.path" size="small" type="info" effect="plain">{{ data.path }}</el-tag>
            </div>
          </div>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="menuVisible = false">取消</el-button>
        <el-button v-permission="'upms:sysrole:edit'" type="primary" @click="submitMenuAssignment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  assignRoleMenus,
  createRole,
  deleteRole,
  queryAssignedRoleMenus,
  queryDepartments,
  queryGrantableMenuTree,
  queryRoleImpact,
  queryRoles,
  updateRole,
} from '@/api/modules'
import { useTablePreferences } from '@/composables/useTablePreferences'
import type { DepartmentView } from '@/types/dept'
import type { MenuTreeNode, MenuType } from '@/api/modules/menu'
import type { RoleView } from '@/types/role'

type MenuDisplayNode = {
  id: number
  label: string
  menuKey: string
  menuType: MenuType
  grantKey?: string
  path?: string
  children?: MenuDisplayNode[]
}

const roles = ref<RoleView[]>([])
const menus = ref<MenuTreeNode[]>([])
const departments = ref<DepartmentView[]>([])
const menuTreeRef = ref<any>(null)
const formRef = ref<FormInstance>()
const roleVisible = ref(false)
const menuVisible = ref(false)
const detailVisible = ref(false)
const editingRoleId = ref<number | null>(null)
const menuTargetRoleId = ref<number | null>(null)
const detailRole = ref<RoleView | null>(null)
const assignedMenuIds = ref<number[]>([])
const assignedCountByRoleId = reactive<Record<number, number>>({})
const focusedMenuId = ref<number | null>(null)
const loading = ref(false)
const keyword = ref('')
const scopeFilter = ref('')
const menuKeyword = ref('')
const selectedMenuIds = ref<number[]>([])
const page = ref(1)
const size = ref(10)

const roleForm = reactive({
  roleCode: '',
  roleName: '',
  roleDesc: '',
  dataScopeType: 'ALL',
  customDeptIds: [] as number[],
})

const roleRules = reactive<FormRules>({
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]{2,64}$/, message: '角色编码仅支持字母、数字、:、_、-', trigger: 'blur' },
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  dataScopeType: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
  customDeptIds: [
    {
      validator: (_rule, value, callback) => {
        if (roleForm.dataScopeType === 'CUSTOM' && (!Array.isArray(value) || value.length === 0)) {
          callback(new Error('自定义范围至少选择一个部门'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
})

const menuTreeData = computed(() => toMenuDisplayTree(menus.value))
const detailMenuTree = computed(() => filterTreeBySelected(menuTreeData.value, new Set(assignedMenuIds.value)))
const departmentTree = computed(() => buildDepartmentTree(departments.value))

const filteredMenuTree = computed(() => {
  const normalizedKeyword = menuKeyword.value.trim().toLowerCase()
  if (!normalizedKeyword) {
    return menuTreeData.value
  }
  return filterTreeByKeyword(menuTreeData.value, normalizedKeyword)
})

const filteredRoles = computed(() =>
  roles.value.filter((role) => {
    const normalizedKeyword = keyword.value.trim().toLowerCase()
    const matchesKeyword =
      !normalizedKeyword ||
      [role.code, role.name, role.description || ''].some((value) => value.toLowerCase().includes(normalizedKeyword))
    const matchesScope = !scopeFilter.value || role.dataScopeType === scopeFilter.value
    return matchesKeyword && matchesScope
  }),
)

const pagedRoles = computed(() => {
  const start = (page.value - 1) * size.value
  return filteredRoles.value.slice(start, start + size.value)
})

const detailMenuSummary = computed(() => summarizeByType(new Set(assignedMenuIds.value), menuTreeData.value))
const selectedMenuSummary = computed(() => summarizeByType(new Set(selectedMenuIds.value), menuTreeData.value))
const allScopeCount = computed(() => filteredRoles.value.filter((item) => item.dataScopeType === 'ALL').length)
const deptScopeCount = computed(() =>
  filteredRoles.value.filter((item) => item.dataScopeType === 'DEPT_AND_CHILDREN' || item.dataScopeType === 'DEPT').length,
)
const customScopeCount = computed(() => filteredRoles.value.filter((item) => item.dataScopeType === 'CUSTOM').length)
const allMenuCount = computed(() => flattenTree(menuTreeData.value).length)

const roleTablePrefs = useTablePreferences('eap.table.roles', [
  { key: 'code', label: '角色编码', width: 140 },
  { key: 'name', label: '角色名称', width: 160 },
  { key: 'description', label: '角色描述', width: 220 },
  { key: 'dataScopeType', label: '数据范围', width: 140 },
  { key: 'menuCount', label: '权限数', width: 100 },
  { key: 'actions', label: '操作', width: 320 },
])

void load()

async function load() {
  loading.value = true
  try {
    const [roleList, menuTree, departmentList] = await Promise.all([queryRoles(), queryGrantableMenuTree(), queryDepartments()])
    roles.value = roleList
    menus.value = menuTree
    departments.value = departmentList
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
}

function resetSearch() {
  keyword.value = ''
  scopeFilter.value = ''
  page.value = 1
}

function handlePageChange() {
  return
}

function openRole(row?: RoleView) {
  editingRoleId.value = row?.id ?? null
  Object.assign(roleForm, {
    roleCode: row?.code ?? '',
    roleName: row?.name ?? '',
    roleDesc: row?.description ?? '',
    dataScopeType: row?.dataScopeType ?? 'ALL',
    customDeptIds: [...(row?.customDeptIds ?? [])],
  })
  roleVisible.value = true
}

async function openDetail(row: RoleView) {
  detailRole.value = row
  assignedMenuIds.value = await queryAssignedRoleMenus(row.id)
  assignedCountByRoleId[row.id] = assignedMenuIds.value.length
  detailVisible.value = true
}

async function submitRole() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
  const payload = {
    roleCode: roleForm.roleCode,
    roleName: roleForm.roleName,
    roleDesc: roleForm.roleDesc || null,
    dataScopeType: roleForm.dataScopeType,
    customDeptIds: roleForm.dataScopeType === 'CUSTOM' ? roleForm.customDeptIds : [],
  }
  if (editingRoleId.value) {
    await updateRole(editingRoleId.value, payload)
    ElMessage.success('角色已更新')
  } else {
    await createRole(payload)
    ElMessage.success('角色已创建')
  }
  roleVisible.value = false
  await load()
}

async function openMenuAssignment(row: RoleView) {
  menuTargetRoleId.value = row.id
  const assignedIds = await queryAssignedRoleMenus(row.id)
  assignedCountByRoleId[row.id] = assignedIds.length
  menuVisible.value = true
  menuKeyword.value = ''
  focusedMenuId.value = null
  await nextTick()
  menuTreeRef.value?.setCheckedKeys(assignedIds)
  selectedMenuIds.value = [...assignedIds]
}

async function submitMenuAssignment() {
  if (!menuTargetRoleId.value) {
    return
  }
  const selectedIds = ((menuTreeRef.value?.getCheckedKeys(false) || []) as Array<string | number>)
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item))

  const assignedIds = await assignRoleMenus(menuTargetRoleId.value, selectedIds)
  assignedCountByRoleId[menuTargetRoleId.value] = assignedIds.length
  menuVisible.value = false
  ElMessage.success('角色权限已更新')

  if (detailRole.value?.id === menuTargetRoleId.value) {
    assignedMenuIds.value = assignedIds
  }
}

function expandAll() {
  setTreeExpanded(true)
}

function collapseAll() {
  setTreeExpanded(false)
}

function syncSelectedMenuIds() {
  selectedMenuIds.value = ((menuTreeRef.value?.getCheckedKeys(false) || []) as Array<string | number>)
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item))
}

function handleMenuNodeClick(data: MenuDisplayNode) {
  focusedMenuId.value = data.id
}

function selectFocusedDescendants() {
  if (focusedMenuId.value == null) {
    return
  }
  const focused = findMenuNode(menuTreeData.value, focusedMenuId.value)
  if (!focused) {
    return
  }
  const checked = new Set<number>(selectedMenuIds.value)
  collectNodeAndDescendantIds(focused).forEach((id) => checked.add(id))
  const checkedIds = Array.from(checked)
  menuTreeRef.value?.setCheckedKeys(checkedIds)
  selectedMenuIds.value = checkedIds
}

function selectByTypes(types: MenuType[]) {
  const targetTypes = new Set<MenuType>(types)
  const checked = new Set<number>(selectedMenuIds.value)
  for (const node of flattenTree(menuTreeData.value)) {
    if (targetTypes.has(node.menuType)) {
      checked.add(node.id)
    }
  }
  const checkedIds = Array.from(checked)
  menuTreeRef.value?.setCheckedKeys(checkedIds)
  selectedMenuIds.value = checkedIds
}

function clearMenuSelection() {
  menuTreeRef.value?.setCheckedKeys([])
  selectedMenuIds.value = []
}

async function removeRole(row: RoleView) {
  const impact = await queryRoleImpact(row.id)
  if (impact.deleteBlocked) {
    ElMessage.warning(impact.warnings[0] || '角色存在引用，暂不允许删除')
    return
  }
  const warningText = impact.warnings.join('\n')
  await ElMessageBox.confirm(
    `${warningText}\n\n确认删除角色 ${row.name} 吗？`,
    '删除影响确认',
    { type: impact.assignedMenuCount > 0 ? 'warning' : 'info' },
  )
  await deleteRole(row.id)
  ElMessage.success('角色已删除')
  await load()
}

function setTreeExpanded(expanded: boolean) {
  const treeStore = (menuTreeRef.value as unknown as { store?: { nodesMap?: Record<string, { expanded: boolean }> } })?.store
  if (!treeStore?.nodesMap) {
    return
  }
  Object.values(treeStore.nodesMap).forEach((node) => {
    node.expanded = expanded
  })
}

function toMenuDisplayTree(source: MenuTreeNode[]): MenuDisplayNode[] {
  return source.map((item) => ({
    id: item.id,
    label: `${item.menuName} (${item.resourceKey})`,
    menuKey: item.resourceKey,
    menuType: item.menuType,
    grantKey: item.grantKey || undefined,
    path: item.path || undefined,
    children: item.children?.length ? toMenuDisplayTree(item.children) : undefined,
  }))
}

function findMenuNode(nodes: MenuDisplayNode[], id: number): MenuDisplayNode | null {
  for (const node of nodes) {
    if (node.id === id) {
      return node
    }
    const child = node.children?.length ? findMenuNode(node.children, id) : null
    if (child) {
      return child
    }
  }
  return null
}

function collectNodeAndDescendantIds(node: MenuDisplayNode) {
  const ids: number[] = []
  const walk = (item: MenuDisplayNode) => {
    ids.push(item.id)
    item.children?.forEach(walk)
  }
  walk(node)
  return ids
}

function flattenTree(nodes: MenuDisplayNode[]): MenuDisplayNode[] {
  const result: MenuDisplayNode[] = []
  const walk = (items: MenuDisplayNode[]) => {
    for (const item of items) {
      result.push(item)
      if (item.children?.length) {
        walk(item.children)
      }
    }
  }
  walk(nodes)
  return result
}

function filterTreeByKeyword(nodes: MenuDisplayNode[], keywordValue: string): MenuDisplayNode[] {
  const filtered: MenuDisplayNode[] = []
  for (const node of nodes) {
    const children = node.children ? filterTreeByKeyword(node.children, keywordValue) : []
    const matched =
      node.label.toLowerCase().includes(keywordValue) ||
      node.menuType.toLowerCase().includes(keywordValue) ||
      (node.path || '').toLowerCase().includes(keywordValue) ||
      (node.grantKey || '').toLowerCase().includes(keywordValue)
    if (matched || children.length > 0) {
      filtered.push({
        ...node,
        children: children.length > 0 ? children : node.children,
      })
    }
  }
  return filtered
}

function filterTreeBySelected(nodes: MenuDisplayNode[], selectedIds: Set<number>): MenuDisplayNode[] {
  const filtered: MenuDisplayNode[] = []
  for (const node of nodes) {
    const children = node.children ? filterTreeBySelected(node.children, selectedIds) : []
    const matched = selectedIds.has(node.id)
    if (matched || children.length > 0) {
      filtered.push({
        ...node,
        children: children.length > 0 ? children : node.children,
      })
    }
  }
  return filtered
}

function summarizeByType(selectedIds: Set<number>, tree: MenuDisplayNode[]) {
  const counter = new Map<MenuType, number>()
  for (const node of flattenTree(tree)) {
    if (!selectedIds.has(node.id)) {
      continue
    }
    counter.set(node.menuType, (counter.get(node.menuType) || 0) + 1)
  }
  return Array.from(counter.entries()).map(([type, count]) => ({ type, count }))
}

function typeLabel(type: MenuType) {
  const labels: Record<MenuType, string> = {
    DIR: '目录',
    MENU: '菜单',
    BUTTON: '按钮',
    API: 'API',
  }
  return labels[type]
}

function buildDepartmentTree(source: DepartmentView[]) {
  const nodes = source.map((item) => ({
    id: item.id,
    label: item.name,
    parentId: item.parentId ?? 0,
    children: [] as Array<{ id: number; label: string; parentId: number; children: unknown[] }>,
  }))
  const map = new Map(nodes.map((item) => [item.id, item]))
  const roots: typeof nodes = []
  nodes.forEach((node) => {
    const parent = node.parentId ? map.get(node.parentId) : null
    if (parent) {
      parent.children.push(node)
      return
    }
    roots.push(node)
  })
  return roots
}

function onRoleHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  roleTablePrefs.setColumnWidth(key, newWidth)
}
</script>

<style scoped lang="scss">
.assignment-toolbar {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}

.assignment-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.menu-node {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.menu-node__meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.permission-tree {
  max-height: 480px;
  overflow-y: auto;
}

.tree-panel {
  margin-top: 20px;
  padding: 16px;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--bg-card-muted);
}

.tree-panel__head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  color: var(--text-soft);
}

.scope-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}
</style>
