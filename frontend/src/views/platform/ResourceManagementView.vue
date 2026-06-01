<template>
  <div class="panel-stack">
    <section class="resource-summary-strip">
      <button class="summary-tile" :class="{ active: typeFilter === '' }" type="button" @click="typeFilter = ''">
        <span>全部节点</span>
        <strong>{{ totalCount }}</strong>
      </button>
      <button class="summary-tile" :class="{ active: typeFilter === 'MENU' }" type="button" @click="typeFilter = 'MENU'">
        <span>菜单</span>
        <strong>{{ menuCount }}</strong>
      </button>
      <button class="summary-tile" :class="{ active: typeFilter === 'BUTTON' }" type="button" @click="typeFilter = 'BUTTON'">
        <span>按钮/API</span>
        <strong>{{ permissionCount }}</strong>
      </button>
      <button class="summary-tile" :class="{ active: systemOnly }" type="button" @click="systemOnly = !systemOnly">
        <span>系统内置</span>
        <strong>{{ systemCount }}</strong>
      </button>
    </section>

    <section v-loading="loading" class="dashboard-panel resource-panel">
      <div class="panel-head resource-panel__head">
        <div>
          <span class="eyebrow">菜单权限</span>
          <h3>菜单管理</h3>
        </div>
        <div class="resource-toolbar">
          <el-input
            v-model="keyword"
            class="resource-toolbar__search"
            clearable
            placeholder="搜索名称、编码、授权标识"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button v-permission="'system:write'" type="primary" :icon="Plus" data-testid="resources-create-root" @click="openCreate(null)">新增顶层</el-button>
        </div>
      </div>

      <el-table
        :data="filteredTableData"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        :row-class-name="rowClassName"
        class="menu-permission-table"
      >
        <el-table-column label="菜单 / 权限" min-width="330">
          <template #default="{ row }">
            <div
              class="node-title"
              :class="{ 'node-title--child': nodeDepth(row) > 0 }"
              :style="nodeTitleStyle(row)"
            >
              <span v-if="nodeDepth(row) > 0" class="node-branch" aria-hidden="true" />
              <span class="node-icon" :class="`node-icon--${row.resourceType.toLowerCase()}`">
                <el-icon><component :is="iconForType(row.resourceType)" /></el-icon>
              </span>
              <div class="node-title__text">
                <div class="node-title__main">
                  <strong>{{ row.resourceName }}</strong>
                  <el-tag :type="tagType(row.resourceType)" effect="plain" size="small">{{ typeLabel(row.resourceType) }}</el-tag>
                  <el-tag v-if="row.system" effect="plain" size="small" type="warning">系统</el-tag>
                </div>
                <small>{{ row.resourceKey }}</small>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="授权与路由" min-width="360">
          <template #default="{ row }">
            <div class="meta-stack">
              <div class="meta-line">
                <span class="meta-label">授权</span>
                <code v-if="row.grantKey">{{ row.grantKey }}</code>
                <span v-else class="meta-empty">未配置</span>
              </div>
              <div v-if="row.resourceType === 'MENU'" class="meta-line meta-line--route">
                <span class="meta-label">路由</span>
                <span class="route-chip">{{ row.routeKey || '-' }}</span>
                <span class="route-path">{{ row.path || '-' }}</span>
              </div>
              <div v-if="row.component" class="meta-line meta-line--route">
                <span class="meta-label">组件</span>
                <span class="route-path">{{ row.component }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <div class="status-stack">
              <span :class="['status-dot', row.visible ? 'is-on' : 'is-muted']">可见</span>
              <span :class="['status-dot', row.enabled ? 'is-on' : 'is-off']">启用</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="排序" width="120">
          <template #default="{ row }">
            <el-input-number
              v-model="sortDrafts[row.id]"
              v-permission="'system:write'"
              :min="0"
              :max="9999"
              size="small"
              controls-position="right"
              @change="(value) => saveSort(row, Number(value || 0))"
            />
          </template>
        </el-table-column>

        <el-table-column label="操作" fixed="right" width="210">
          <template #default="{ row }">
            <div class="row-actions">
              <el-tooltip content="新增下级" placement="top">
                <el-button
                  v-permission="'system:write'"
                  circle
                  :icon="Plus"
                  type="primary"
                  plain
                  :disabled="!canAddChild(row)"
                  @click="openCreate(row)"
                />
              </el-tooltip>
              <el-tooltip content="编辑" placement="top">
                <el-button v-permission="'system:write'" circle :icon="Edit" type="primary" plain data-testid="resources-edit" @click="openEdit(row)" />
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button v-permission="'system:write'" circle :icon="Delete" type="danger" plain data-testid="resources-delete" :disabled="row.system" @click="removeResource(row)" />
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无菜单或权限节点" />
        </template>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增菜单/权限' : '编辑菜单/权限'" width="760px">
      <el-form ref="formRef" label-position="top" :model="resourceForm" :rules="formRules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="上级节点" prop="parentId">
              <el-tree-select
                v-model="resourceForm.parentId"
                :data="parentTreeOptions"
                node-key="id"
                check-strictly
                default-expand-all
                :props="{ value: 'id', label: 'label', children: 'children', disabled: 'disabled' }"
                style="width: 100%"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="resourceType">
              <el-segmented v-model="resourceForm.resourceType" :options="typeOptions" @change="handleTypeChange" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="名称" prop="resourceName">
              <el-input v-model.trim="resourceForm.resourceName" placeholder="例如 用户管理" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="编码" prop="resourceKey">
              <el-input v-model.trim="resourceForm.resourceKey" placeholder="例如 users.create" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="resourceForm.resourceType === 'MENU'" :gutter="16">
          <el-col :span="12">
            <el-form-item label="路由标识" prop="routeKey">
              <el-input v-model.trim="resourceForm.routeKey" placeholder="例如 users" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路由路径" prop="path">
              <el-input v-model.trim="resourceForm.path" placeholder="/system/users" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="resourceForm.resourceType === 'MENU'" :gutter="16">
          <el-col :span="12">
            <el-form-item label="组件" prop="component">
              <el-input v-model.trim="resourceForm.component" placeholder="UsersView" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="读权限" prop="grantKey">
              <el-input v-model.trim="resourceForm.grantKey" placeholder="例如 user:read" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="resourceForm.resourceType === 'BUTTON' || resourceForm.resourceType === 'API'" :gutter="16">
          <el-col :span="12">
            <el-form-item :label="resourceForm.resourceType === 'BUTTON' ? '操作权限' : '接口权限'" prop="grantKey">
              <el-input v-model.trim="resourceForm.grantKey" placeholder="例如 user:write" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col v-if="resourceForm.resourceType === 'DIR' || resourceForm.resourceType === 'MENU'" :span="12">
            <el-form-item label="图标">
              <el-input v-model.trim="resourceForm.icon" placeholder="例如 Setting" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="resourceForm.orderNo" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="可见">
              <el-switch v-model="resourceForm.visible" inline-prompt active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="resourceForm.enabled" inline-prompt active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="'system:write'" type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Aim, Delete, Edit, Folder, Menu as MenuIcon, Plus, Refresh, Search, SwitchButton } from '@element-plus/icons-vue'
import {
  createResource,
  deleteResource,
  queryResourceTree,
  sortResource,
  updateResource,
} from '@/api/modules'
import type { ResourceTreeNode, ResourceType } from '@/types/resource'

type ParentOption = {
  id: number
  label: string
  disabled: boolean
  children?: ParentOption[]
}

type ResourceFilter = ResourceType | ''

const formRef = ref<FormInstance>()
const resources = ref<ResourceTreeNode[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingResourceId = ref<number | null>(null)
const keyword = ref('')
const typeFilter = ref<ResourceFilter>('')
const systemOnly = ref(false)
const sortDrafts = reactive<Record<number, number>>({})

const resourceForm = reactive({
  parentId: null as number | null,
  resourceType: 'MENU' as ResourceType,
  resourceKey: '',
  resourceName: '',
  routeKey: '',
  grantKey: '',
  path: '',
  component: '',
  icon: '',
  orderNo: 0,
  visible: true,
  enabled: true,
})

const typeOptions: Array<{ label: string; value: ResourceType }> = [
  { label: '目录', value: 'DIR' },
  { label: '菜单', value: 'MENU' },
  { label: '按钮', value: 'BUTTON' },
  { label: 'API', value: 'API' },
]

const formRules = reactive<FormRules>({
  parentId: [{ validator: validateParent, trigger: 'change' }],
  resourceType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  resourceKey: [
    { required: true, message: '请输入编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9._:-]{2,128}$/, message: '编码仅支持字母、数字、点、下划线、冒号和中划线', trigger: 'blur' },
  ],
  resourceName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  routeKey: [{ validator: requireWhenMenu('请输入路由标识'), trigger: 'blur' }],
  path: [{ validator: requireWhenMenu('请输入路由路径'), trigger: 'blur' }],
  component: [{ validator: requireWhenMenu('请输入组件名称'), trigger: 'blur' }],
  grantKey: [
    { validator: validateGrantKey, trigger: 'blur' },
    { validator: validateGrantKey, trigger: 'change' },
  ],
})

const flatNodes = computed(() => flattenResources(resources.value))
const filteredTableData = computed(() => filterResources(resources.value, keyword.value.trim().toLowerCase()))
const totalCount = computed(() => flatNodes.value.length)
const menuCount = computed(() => flatNodes.value.filter((item) => item.resourceType === 'DIR' || item.resourceType === 'MENU').length)
const permissionCount = computed(() => flatNodes.value.filter((item) => item.resourceType === 'BUTTON' || item.resourceType === 'API').length)
const systemCount = computed(() => flatNodes.value.filter((item) => item.system).length)

const parentTreeOptions = computed<ParentOption[]>(() => {
  const exclude = editingResourceId.value == null ? new Set<number>() : collectDescendantIds(editingResourceId.value)
  return toParentOptions(resources.value, exclude, resourceForm.resourceType)
})

void load()

async function load() {
  loading.value = true
  try {
    resources.value = await queryResourceTree()
    syncSortDrafts()
  } finally {
    loading.value = false
  }
}

function openCreate(parent: ResourceTreeNode | null) {
  dialogMode.value = 'create'
  editingResourceId.value = null
  const resourceType = inferDefaultType(parent)
  Object.assign(resourceForm, {
    parentId: resolveDefaultParentId(resourceType, parent),
    resourceType,
    resourceKey: '',
    resourceName: '',
    routeKey: '',
    grantKey: '',
    path: '',
    component: '',
    icon: '',
    orderNo: nextOrderNo(parent),
    visible: resourceType !== 'API',
    enabled: true,
  })
  dialogVisible.value = true
}

function openEdit(row: ResourceTreeNode) {
  dialogMode.value = 'edit'
  editingResourceId.value = row.id
  Object.assign(resourceForm, {
    parentId: row.parentId ?? null,
    resourceType: row.resourceType,
    resourceKey: row.resourceKey,
    resourceName: row.resourceName,
    routeKey: row.routeKey || '',
    grantKey: row.grantKey || '',
    path: row.path || '',
    component: row.component || '',
    icon: row.icon || '',
    orderNo: row.orderNo ?? 0,
    visible: row.visible,
    enabled: row.enabled,
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
  const payload = {
    parentId: resourceForm.parentId,
    resourceType: resourceForm.resourceType,
    resourceKey: resourceForm.resourceKey.trim(),
    resourceName: resourceForm.resourceName.trim(),
    routeKey: resourceForm.resourceType === 'MENU' ? blankToNull(resourceForm.routeKey) : null,
    grantKey: requiresGrant(resourceForm.resourceType) ? blankToNull(resourceForm.grantKey) : null,
    path: resourceForm.resourceType === 'MENU' ? blankToNull(resourceForm.path) : null,
    component: resourceForm.resourceType === 'MENU' ? blankToNull(resourceForm.component) : null,
    icon: resourceForm.resourceType === 'DIR' || resourceForm.resourceType === 'MENU' ? blankToNull(resourceForm.icon) : null,
    orderNo: resourceForm.orderNo ?? 0,
    visible: resourceForm.visible,
    enabled: resourceForm.enabled,
  }
  if (dialogMode.value === 'create') {
    await createResource(payload)
    ElMessage.success('菜单/权限已创建')
  } else if (editingResourceId.value != null) {
    await updateResource(editingResourceId.value, payload)
    ElMessage.success('菜单/权限已更新')
  }
  dialogVisible.value = false
  await load()
}

async function removeResource(row: ResourceTreeNode) {
  await ElMessageBox.confirm('删除后不可恢复，且已分配给角色的节点不能删除。是否继续？', '删除确认', { type: 'warning' })
  await deleteResource(row.id)
  ElMessage.success('菜单/权限已删除')
  await load()
}

async function saveSort(row: ResourceTreeNode, orderNo: number) {
  if (row.orderNo === orderNo) {
    return
  }
  await sortResource(row.id, orderNo)
  ElMessage.success('排序已更新')
  await load()
}

function handleTypeChange() {
  const parent = flatNodes.value.find((item) => item.id === resourceForm.parentId) || null
  if (resourceForm.resourceType === 'API' && (!parent || parent.resourceType === 'DIR')) {
    resourceForm.parentId = resolveDefaultParentId(resourceForm.resourceType, null)
  } else if (!parent || !isValidParentForType(parent.resourceType, resourceForm.resourceType)) {
    resourceForm.parentId = resolveDefaultParentId(resourceForm.resourceType, null)
  }
  if (resourceForm.resourceType === 'API') {
    resourceForm.visible = false
  }
  if (!requiresGrant(resourceForm.resourceType)) {
    resourceForm.grantKey = ''
  }
}

function validateParent(_rule: unknown, value: number | null, callback: (error?: Error) => void) {
  const parent = flatNodes.value.find((item) => item.id === value) || null
  if (resourceForm.resourceType === 'BUTTON' && parent?.resourceType !== 'MENU') {
    callback(new Error('按钮权限必须选择菜单作为上级节点'))
    return
  }
  if (parent && !isValidParentForType(parent.resourceType, resourceForm.resourceType)) {
    callback(new Error('当前类型不能挂在所选上级节点下'))
    return
  }
  callback()
}

function requireWhenMenu(message: string) {
  return (_rule: unknown, value: string, callback: (error?: Error) => void) => {
    if (resourceForm.resourceType === 'MENU' && !value?.trim()) {
      callback(new Error(message))
      return
    }
    callback()
  }
}

function validateGrantKey(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!requiresGrant(resourceForm.resourceType)) {
    callback()
    return
  }
  if (!value?.trim()) {
    callback(new Error(resourceForm.resourceType === 'MENU' ? '请输入读权限授权标识' : '请输入权限授权标识'))
    return
  }
  if (!/^[a-zA-Z0-9]+:[a-zA-Z0-9]+(?::[a-zA-Z0-9_-]+)?$/.test(value.trim())) {
    callback(new Error('授权标识格式应类似 user:read 或 user:write'))
    return
  }
  callback()
}

function syncSortDrafts() {
  for (const node of flatNodes.value) {
    sortDrafts[node.id] = node.orderNo ?? 0
  }
}

function flattenResources(nodes: ResourceTreeNode[]): ResourceTreeNode[] {
  const result: ResourceTreeNode[] = []
  const walk = (items: ResourceTreeNode[]) => {
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

function filterResources(nodes: ResourceTreeNode[], normalizedKeyword: string): ResourceTreeNode[] {
  const filtered: ResourceTreeNode[] = []
  for (const node of nodes) {
    const children = filterResources(node.children || [], normalizedKeyword)
    const matchedKeyword =
      !normalizedKeyword ||
      [
        node.resourceName,
        node.resourceKey,
        node.resourceType,
        node.grantKey || '',
        node.routeKey || '',
        node.path || '',
        node.component || '',
      ].some((value) => value.toLowerCase().includes(normalizedKeyword))
    const matchedType =
      !typeFilter.value ||
      node.resourceType === typeFilter.value ||
      (typeFilter.value === 'MENU' && node.resourceType === 'DIR') ||
      (typeFilter.value === 'BUTTON' && node.resourceType === 'API')
    const matchedSystem = !systemOnly.value || node.system
    if ((matchedKeyword && matchedType && matchedSystem) || children.length > 0) {
      filtered.push({ ...node, children })
    }
  }
  return filtered
}

function toParentOptions(nodes: ResourceTreeNode[], exclude: Set<number>, childType: ResourceType): ParentOption[] {
  return nodes
    .filter((item) => !exclude.has(item.id))
    .map((item) => ({
      id: item.id,
      label: `${item.resourceName} (${typeLabel(item.resourceType)})`,
      disabled: !isValidParentForType(item.resourceType, childType),
      children: item.children?.length ? toParentOptions(item.children, exclude, childType) : undefined,
    }))
}

function collectDescendantIds(resourceId: number) {
  const ids = new Set<number>()
  const target = flatNodes.value.find((item) => item.id === resourceId)
  if (!target) {
    return ids
  }
  ids.add(resourceId)
  const markChildren = (parent: ResourceTreeNode) => {
    for (const child of parent.children || []) {
      ids.add(child.id)
      markChildren(child)
    }
  }
  markChildren(target)
  return ids
}

function inferDefaultType(parent: ResourceTreeNode | null): ResourceType {
  if (parent?.resourceType === 'MENU') {
    return 'BUTTON'
  }
  return 'MENU'
}

function resolveDefaultParentId(type: ResourceType, preferredParent: ResourceTreeNode | null) {
  if (preferredParent && isValidParentForType(preferredParent.resourceType, type)) {
    return preferredParent.id
  }
  if (type === 'API') {
    const apiDir = flatNodes.value.find((item) => item.resourceType === 'DIR' && item.resourceKey === 'api')
    if (apiDir) {
      return apiDir.id
    }
  }
  const root = flatNodes.value.find((item) => item.resourceKey === 'root')
  if (root && isValidParentForType(root.resourceType, type)) {
    return root.id
  }
  return null
}

function nextOrderNo(parent: ResourceTreeNode | null) {
  const siblings = parent?.children || resources.value
  const max = siblings.reduce((value, item) => Math.max(value, item.orderNo ?? 0), 0)
  return max + 10
}

function canAddChild(row: ResourceTreeNode) {
  return row.resourceType === 'DIR' || row.resourceType === 'MENU'
}

function isValidParentForType(parentType: ResourceType, childType: ResourceType) {
  if (parentType === 'BUTTON' || parentType === 'API') {
    return false
  }
  if (parentType === 'MENU') {
    return childType === 'BUTTON' || childType === 'API'
  }
  if (childType === 'BUTTON') {
    return false
  }
  return true
}

function requiresGrant(type: ResourceType) {
  return type === 'MENU' || type === 'BUTTON' || type === 'API'
}

function typeLabel(type: ResourceType) {
  const labels: Record<ResourceType, string> = {
    DIR: '目录',
    MENU: '菜单',
    BUTTON: '按钮',
    API: 'API',
  }
  return labels[type]
}

function tagType(type: ResourceType) {
  const types: Record<ResourceType, 'primary' | 'success' | 'warning' | 'info'> = {
    DIR: 'info',
    MENU: 'primary',
    BUTTON: 'success',
    API: 'warning',
  }
  return types[type]
}

function iconForType(type: ResourceType) {
  const icons = {
    DIR: Folder,
    MENU: MenuIcon,
    BUTTON: SwitchButton,
    API: Aim,
  }
  return icons[type]
}

function nodeDepth(row: ResourceTreeNode) {
  if (!row.ancestors) {
    return 0
  }
  return row.ancestors.split(',').filter(Boolean).length
}

function nodeTitleStyle(row: ResourceTreeNode) {
  const depth = Math.min(nodeDepth(row), 6)
  return {
    '--branch-left': `${Math.max(depth * 30 - 18, 0)}px`,
    paddingLeft: `${depth * 30}px`,
  }
}

function rowClassName({ row }: { row: ResourceTreeNode }) {
  const classes = [`resource-row--depth-${Math.min(nodeDepth(row), 4)}`]
  if (row.resourceType === 'BUTTON' || row.resourceType === 'API') {
    classes.push('resource-row--leaf')
  }
  return classes.join(' ')
}

function blankToNull(value?: string | null) {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}
</script>

<style scoped lang="scss">
.menu-permission-table {
  width: 100%;
  margin-top: 8px;
}

.menu-permission-table :deep(.el-table__row) {
  height: 76px;
}

.menu-permission-table :deep(.resource-row--leaf) {
  background: var(--bg-card-muted);
}

.menu-permission-table :deep(.resource-row--depth-1 > td:first-child) {
  box-shadow: inset 3px 0 0 color-mix(in srgb, var(--accent) 22%, transparent);
}

.menu-permission-table :deep(.resource-row--depth-2 > td:first-child) {
  box-shadow: inset 3px 0 0 color-mix(in srgb, var(--accent-2) 28%, transparent);
}

.menu-permission-table :deep(.resource-row--depth-3 > td:first-child),
.menu-permission-table :deep(.resource-row--depth-4 > td:first-child) {
  box-shadow: inset 3px 0 0 color-mix(in srgb, var(--accent-2) 22%, transparent);
}

.menu-permission-table :deep(.resource-row--depth-2) {
  background: var(--accent-soft);
}

.menu-permission-table :deep(.resource-row--depth-3),
.menu-permission-table :deep(.resource-row--depth-4) {
  background: var(--accent-2-soft);
}

.menu-permission-table :deep(.el-table__cell) {
  vertical-align: middle;
}

.node-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  position: relative;
  transition: padding-left 0.16s ease;
}

.node-title--child {
  min-height: 40px;
}

.node-branch {
  position: absolute;
  left: var(--branch-left, 0);
  top: 4px;
  bottom: 4px;
  width: 14px;
  border-left: 1px solid var(--line-strong);
  border-bottom: 1px solid var(--line-strong);
  border-bottom-left-radius: 6px;
}

.node-icon {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: var(--text-soft);
  background: var(--bg-card-muted);
}

.node-icon--menu {
  color: var(--accent);
  background: var(--accent-soft);
}

.node-icon--button {
  color: var(--accent-2);
  background: var(--accent-2-soft);
}

.node-icon--api {
  color: var(--warning);
  background: color-mix(in srgb, var(--warning) 12%, #fff);
}

.node-title__text {
  min-width: 0;
}

.node-title__main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.node-title strong {
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-title small {
  display: block;
  margin-top: 3px;
  color: var(--text-soft);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-stack {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.meta-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--text-soft);
}

.meta-label {
  width: 34px;
  flex: 0 0 34px;
  color: var(--text-soft);
  font-size: 12px;
}

.meta-line code {
  border-radius: 8px;
  background: var(--bg-card-muted);
  color: var(--text-main);
  padding: 2px 7px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
}

.meta-empty {
  color: var(--text-soft);
}

.route-chip {
  flex: 0 0 auto;
  border: 1px solid rgba(22, 119, 255, 0.2);
  border-radius: 8px;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 1px 7px;
  font-size: 12px;
}

.route-path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-stack {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--text-soft);
  font-size: 12px;
}

.status-dot::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--line-strong);
}

.status-dot.is-on::before {
  background: var(--success);
}

.status-dot.is-off::before {
  background: var(--danger);
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.menu-permission-table :deep(.el-input-number--small) {
  width: 96px;
}

@media (max-width: 1100px) {
  .menu-permission-table :deep(.el-input-number--small) {
    width: 100%;
  }
}
</style>
