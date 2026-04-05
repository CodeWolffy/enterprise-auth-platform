<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">资源</span>
        <strong>{{ totalCount }}</strong>
        <span>资源总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">菜单</span>
        <strong>{{ menuCount }}</strong>
        <span>目录 + 菜单节点</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">权限</span>
        <strong>{{ actionCount }}</strong>
        <span>按钮 + API 节点</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">系统</span>
        <strong>{{ systemCount }}</strong>
        <span>系统内置节点</span>
      </article>
    </section>

    <section v-loading="loading" class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">资源</span>
          <h3>菜单管理</h3>
        </div>
        <div class="resource-toolbar">
          <el-button @click="load">刷新</el-button>
          <el-button type="primary" @click="openCreate(false)">新增顶层节点</el-button>
          <el-button type="primary" plain :disabled="!canAddChild" @click="openCreate(true)">新增子节点</el-button>
        </div>
      </div>

      <div class="resource-layout">
        <article class="tree-pane">
          <el-tree
            ref="resourceTreeRef"
            :data="treeData"
            node-key="id"
            default-expand-all
            highlight-current
            :current-node-key="selectedId || undefined"
            @node-click="handleNodeClick"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span>{{ data.label }}</span>
                <div class="tree-node__tags">
                  <el-tag size="small" effect="plain">{{ data.resourceType }}</el-tag>
                  <el-tag v-if="data.system" size="small" effect="plain" type="warning">系统</el-tag>
                </div>
              </div>
            </template>
          </el-tree>
        </article>

        <article class="detail-pane">
          <template v-if="selectedNode">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="资源 ID">{{ selectedNode.id }}</el-descriptions-item>
              <el-descriptions-item label="资源类型">{{ selectedNode.resourceType }}</el-descriptions-item>
              <el-descriptions-item label="资源键">{{ selectedNode.resourceKey }}</el-descriptions-item>
              <el-descriptions-item label="资源名称">{{ selectedNode.resourceName }}</el-descriptions-item>
              <el-descriptions-item label="父节点 ID">{{ selectedNode.parentId ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="祖先链">{{ selectedNode.ancestors || '-' }}</el-descriptions-item>
              <el-descriptions-item label="路由键">{{ selectedNode.routeKey || '-' }}</el-descriptions-item>
              <el-descriptions-item label="授权键">{{ selectedNode.grantKey || '-' }}</el-descriptions-item>
              <el-descriptions-item label="路径">{{ selectedNode.path || '-' }}</el-descriptions-item>
              <el-descriptions-item label="组件">{{ selectedNode.component || '-' }}</el-descriptions-item>
              <el-descriptions-item label="图标">{{ selectedNode.icon || '-' }}</el-descriptions-item>
              <el-descriptions-item label="排序值">{{ selectedNode.orderNo ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="可见">{{ selectedNode.visible ? '是' : '否' }}</el-descriptions-item>
              <el-descriptions-item label="启用">{{ selectedNode.enabled ? '是' : '否' }}</el-descriptions-item>
              <el-descriptions-item label="系统资源">{{ selectedNode.system ? '是' : '否' }}</el-descriptions-item>
            </el-descriptions>

            <div class="detail-actions">
              <el-button type="primary" @click="openEdit">编辑</el-button>
              <el-button type="primary" plain :disabled="!canAddChild" @click="openCreate(true)">新增子节点</el-button>
              <el-button type="danger" :disabled="Boolean(selectedNode.system)" @click="removeResource">删除</el-button>
            </div>

            <div class="sort-box">
              <span>排序值</span>
              <el-input-number v-model="sortDraft" :min="0" :max="9999" />
              <el-button @click="saveSort">保存排序</el-button>
            </div>
          </template>
          <el-empty v-else description="请选择左侧资源节点" />
        </article>
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增资源' : '编辑资源'" width="720px">
      <el-form ref="formRef" label-position="top" :model="resourceForm" :rules="formRules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="父节点" prop="parentId">
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
            <el-form-item label="资源类型" prop="resourceType">
              <el-select v-model="resourceForm.resourceType" style="width: 100%">
                <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="资源键" prop="resourceKey">
              <el-input v-model.trim="resourceForm.resourceKey" placeholder="如 users.manage" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="资源名称" prop="resourceName">
              <el-input v-model.trim="resourceForm.resourceName" placeholder="如 用户管理" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="路由键">
              <el-input v-model.trim="resourceForm.routeKey" placeholder="如 users / settings" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授权键">
              <el-input v-model.trim="resourceForm.grantKey" placeholder="如 user:read / user:write" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="路径">
              <el-input v-model.trim="resourceForm.path" placeholder="/system/users" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="组件">
              <el-input v-model.trim="resourceForm.component" placeholder="UsersView" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="图标">
              <el-input v-model.trim="resourceForm.icon" placeholder="Avatar" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序值">
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
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createResource,
  deleteResource,
  queryResourceTree,
  sortResource,
  updateResource,
} from '@/api/platform'
import type { ResourceTreeNode, ResourceType } from '@/types/auth'

type TreeNode = {
  id: number
  label: string
  resourceType: ResourceType
  system: boolean
  children?: TreeNode[]
}

type ParentOption = {
  id: number
  label: string
  disabled: boolean
  children?: ParentOption[]
}

const resourceTreeRef = ref<any>(null)
const formRef = ref<FormInstance>()
const resources = ref<ResourceTreeNode[]>([])
const loading = ref(false)
const selectedId = ref<number | null>(null)
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingResourceId = ref<number | null>(null)
const sortDraft = ref(0)

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
  { label: '目录 DIR', value: 'DIR' },
  { label: '菜单 MENU', value: 'MENU' },
  { label: '按钮 BUTTON', value: 'BUTTON' },
  { label: '接口 API', value: 'API' },
]

const formRules = reactive<FormRules>({
  resourceType: [{ required: true, message: '请选择资源类型', trigger: 'change' }],
  resourceKey: [
    { required: true, message: '请输入资源键', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9._:-]{2,128}$/, message: '仅支持字母、数字、.、_、:、-', trigger: 'blur' },
  ],
  resourceName: [{ required: true, message: '请输入资源名称', trigger: 'blur' }],
})

const flatNodes = computed(() => flattenResources(resources.value))
const selectedNode = computed(() => flatNodes.value.find((item) => item.id === selectedId.value) || null)
const treeData = computed<TreeNode[]>(() => toTreeNodes(resources.value))

const totalCount = computed(() => flatNodes.value.length)
const menuCount = computed(() => flatNodes.value.filter((item) => item.resourceType === 'DIR' || item.resourceType === 'MENU').length)
const actionCount = computed(() => flatNodes.value.filter((item) => item.resourceType === 'BUTTON' || item.resourceType === 'API').length)
const systemCount = computed(() => flatNodes.value.filter((item) => item.system).length)
const canAddChild = computed(() => Boolean(selectedNode.value && !isLeafType(selectedNode.value.resourceType)))

const parentTreeOptions = computed<ParentOption[]>(() => {
  const exclude = editingResourceId.value == null ? new Set<number>() : collectDescendantIds(editingResourceId.value)
  return toParentOptions(resources.value, exclude)
})

void load()

async function load() {
  loading.value = true
  try {
    resources.value = await queryResourceTree()
    if (!selectedId.value || !flatNodes.value.some((item) => item.id === selectedId.value)) {
      selectedId.value = resources.value[0]?.id ?? null
    }
    syncSortDraft()
  } finally {
    loading.value = false
  }
}

function handleNodeClick(node: TreeNode) {
  selectedId.value = node.id
  syncSortDraft()
}

function syncSortDraft() {
  sortDraft.value = selectedNode.value?.orderNo ?? 0
}

function openCreate(asChild: boolean) {
  dialogMode.value = 'create'
  editingResourceId.value = null
  const rootId = resources.value.find((item) => item.resourceKey === 'root')?.id ?? null
  const parent = asChild ? selectedNode.value : null
  if (asChild && parent && isLeafType(parent.resourceType)) {
    ElMessage.warning('按钮/API 节点下不能创建子节点')
    return
  }
  Object.assign(resourceForm, {
    parentId: asChild ? parent?.id ?? null : rootId,
    resourceType: inferDefaultType(parent?.resourceType),
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
  dialogVisible.value = true
}

function openEdit() {
  if (!selectedNode.value) {
    return
  }
  dialogMode.value = 'edit'
  editingResourceId.value = selectedNode.value.id
  Object.assign(resourceForm, {
    parentId: selectedNode.value.parentId ?? null,
    resourceType: selectedNode.value.resourceType,
    resourceKey: selectedNode.value.resourceKey,
    resourceName: selectedNode.value.resourceName,
    routeKey: selectedNode.value.routeKey || '',
    grantKey: selectedNode.value.grantKey || '',
    path: selectedNode.value.path || '',
    component: selectedNode.value.component || '',
    icon: selectedNode.value.icon || '',
    orderNo: selectedNode.value.orderNo ?? 0,
    visible: selectedNode.value.visible,
    enabled: selectedNode.value.enabled,
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
    routeKey: blankToNull(resourceForm.routeKey),
    grantKey: blankToNull(resourceForm.grantKey),
    path: blankToNull(resourceForm.path),
    component: blankToNull(resourceForm.component),
    icon: blankToNull(resourceForm.icon),
    orderNo: resourceForm.orderNo ?? 0,
    visible: resourceForm.visible,
    enabled: resourceForm.enabled,
  }
  if (dialogMode.value === 'create') {
    await createResource(payload)
    ElMessage.success('资源已创建')
  } else if (editingResourceId.value != null) {
    await updateResource(editingResourceId.value, payload)
    ElMessage.success('资源已更新')
  }
  dialogVisible.value = false
  await load()
}

async function removeResource() {
  if (!selectedNode.value) {
    return
  }
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除确认', { type: 'warning' })
  await deleteResource(selectedNode.value.id)
  ElMessage.success('资源已删除')
  await load()
}

async function saveSort() {
  if (!selectedNode.value) {
    return
  }
  await sortResource(selectedNode.value.id, Number(sortDraft.value || 0))
  ElMessage.success('排序已更新')
  await load()
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

function toTreeNodes(nodes: ResourceTreeNode[]): TreeNode[] {
  return nodes.map((item) => ({
    id: item.id,
    label: `${item.resourceName} (${item.resourceKey})`,
    resourceType: item.resourceType,
    system: item.system,
    children: item.children?.length ? toTreeNodes(item.children) : undefined,
  }))
}

function toParentOptions(nodes: ResourceTreeNode[], exclude: Set<number>): ParentOption[] {
  return nodes
    .filter((item) => !exclude.has(item.id))
    .map((item) => ({
      id: item.id,
      label: `${item.resourceName} (${item.resourceKey})`,
      disabled: isLeafType(item.resourceType),
      children: item.children?.length ? toParentOptions(item.children, exclude) : undefined,
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

function inferDefaultType(parentType?: ResourceType) {
  if (parentType === 'MENU') {
    return 'BUTTON' as ResourceType
  }
  if (parentType === 'DIR') {
    return 'MENU' as ResourceType
  }
  return 'MENU' as ResourceType
}

function isLeafType(type: ResourceType) {
  return type === 'BUTTON' || type === 'API'
}

function blankToNull(value?: string | null) {
  const trimmed = value?.trim()
  return trimmed ? trimmed : null
}
</script>

<style scoped lang="scss">
.resource-toolbar {
  display: flex;
  gap: 10px;
}

.resource-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: 360px minmax(0, 1fr);
}

.tree-pane,
.detail-pane {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.65);
  padding: 14px;
}

.tree-pane {
  max-height: 720px;
  overflow: auto;
}

.tree-node {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.tree-node__tags {
  display: flex;
  gap: 8px;
}

.detail-actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
}

.sort-box {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

@media (max-width: 1200px) {
  .resource-layout {
    grid-template-columns: 1fr;
  }
}
</style>
