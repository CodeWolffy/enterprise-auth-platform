<template>
  <div class="panel-stack">
    <section v-loading="loading" class="dashboard-panel resource-panel">
      <div class="panel-head resource-panel__head">
        <div>
          <span class="eyebrow">菜单配置</span>
          <h3>菜单管理</h3>
        </div>
        <div class="resource-toolbar">
          <el-input
            v-model="keyword"
            class="resource-toolbar__search"
            clearable
            placeholder="搜索名称、资源、路由、授权键或应用标识"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button v-permission="'system:write'" type="primary" :icon="Plus" @click="openCreate(null)">
            新增顶层
          </el-button>
        </div>
      </div>

      <el-table
        :data="filteredTableData"
        row-key="id"
        default-expand-all
        :tree-props="{ children: 'children' }"
        class="menu-permission-table"
        size="default"
      >
        <el-table-column label="菜单名称" min-width="260">
          <template #default="{ row }">
            <div class="node-title" :style="nodeTitleStyle(row)">
              <el-icon v-if="row.menuType === 'DIR'" class="node-icon node-icon--dir">
                <FolderOpened />
              </el-icon>
              <el-icon v-else-if="row.menuType === 'MENU'" class="node-icon node-icon--menu">
                <Menu />
              </el-icon>
              <el-icon v-else class="node-icon node-icon--permission">
                <Tickets />
              </el-icon>
              <span class="node-label">{{ row.menuName }}</span>
              <el-tag size="small" effect="plain">{{ typeLabel(row.menuType) }}</el-tag>
              <el-tag v-if="row.system" size="small" type="warning" effect="plain">系统</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="资源标识" width="180">
          <template #default="{ row }">
            <code class="route-key">{{ row.resourceKey }}</code>
          </template>
        </el-table-column>

        <el-table-column label="授权键" width="180">
          <template #default="{ row }">
            <code v-if="row.grantKey">{{ row.grantKey }}</code>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="路由标识" width="160">
          <template #default="{ row }">
            <code v-if="row.routeKey" class="route-key">{{ row.routeKey }}</code>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="路由路径" width="160">
          <template #default="{ row }">
            <code v-if="row.path">{{ row.path }}</code>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="组件" width="180">
          <template #default="{ row }">
            <span v-if="row.component">{{ row.component }}</span>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="应用标识" width="140">
          <template #default="{ row }">
            <code v-if="row.applicationKey" class="route-key">{{ row.applicationKey }}</code>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="外链" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.outerStatus" size="small" type="warning" effect="plain">外链</el-tag>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="图标" width="80" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.icon" :size="18"><component :is="row.icon" /></el-icon>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="!row.visible" size="small" type="info">隐藏</el-tag>
            <el-tag v-else-if="!row.enabled" size="small" type="danger">禁用</el-tag>
            <el-tag v-else size="small" type="success">正常</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="排序" width="90" align="center">
          <template #default="{ row }">
            <span class="sort-value">{{ row.orderNo }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.grantKey"
              link
              type="primary"
              size="small"
              @click="copyGrantKey(row)"
            >
              复制权限
            </el-button>
            <el-button
              v-if="row.menuType === 'MENU'"
              v-permission="'system:write'"
              link
              type="primary"
              size="small"
              @click="openBatchActions(row)"
            >
              批量按钮
            </el-button>
            <el-button
              v-permission="'system:write'"
              link
              type="primary"
              size="small"
              :icon="Plus"
              @click="openCreate(row.id)"
            >
              新增子级
            </el-button>
            <el-button
              v-permission="'system:write'"
              link
              type="primary"
              size="small"
              :icon="Edit"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'system:write'"
              link
              type="danger"
              size="small"
              :icon="Delete"
              :disabled="row.system"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑菜单' : '新增菜单'"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="90px"
        label-position="right"
      >
        <el-form-item v-if="!isEditing" label="上级节点">
          <el-tree-select
            v-model="form.parentId"
            :data="menuTreeForSelect"
            :props="{ label: 'menuName', value: 'id', children: 'children' }"
            check-strictly
            clearable
            placeholder="留空为顶层节点"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="菜单类型" prop="menuType">
          <el-segmented v-model="form.menuType" :options="menuTypeOptions" />
        </el-form-item>

        <el-form-item label="资源标识" prop="resourceKey">
          <el-input v-model="form.resourceKey" placeholder="唯一标识，如 system:user:create" />
        </el-form-item>

        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="输入菜单名称" maxlength="60" />
        </el-form-item>

        <el-form-item v-if="form.menuType !== 'BUTTON' && form.menuType !== 'API'" label="路由标识" prop="routeKey">
          <el-input v-model="form.routeKey" placeholder="前端路由标识，如 users" />
        </el-form-item>

        <el-form-item v-if="form.menuType === 'MENU' || form.menuType === 'BUTTON' || form.menuType === 'API'" label="授权键" prop="grantKey">
          <el-input v-model="form.grantKey" placeholder="授权键，如 user:read 或 user:write:create" />
        </el-form-item>

        <el-form-item v-if="form.menuType === 'MENU'" label="路由路径" prop="path">
          <el-input v-model="form.path" placeholder="/system/users" />
        </el-form-item>

        <el-form-item v-if="form.menuType === 'MENU'" label="组件名称" prop="component">
          <el-input v-model="form.component" placeholder="UsersView" />
        </el-form-item>

        <el-form-item label="重定向" v-if="form.menuType === 'MENU'">
          <el-input v-model="form.redirect" placeholder="重定向路径（可选）" />
        </el-form-item>

        <el-form-item label="图标" v-if="form.menuType === 'DIR' || form.menuType === 'MENU'">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名">
            <template #prefix>
              <el-icon v-if="form.icon"><component :is="form.icon" /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="应用标识">
          <el-input v-model="form.applicationKey" placeholder="用于套餐/能力映射，如 app_base" clearable />
        </el-form-item>

        <el-form-item v-if="form.menuType === 'MENU'" label="外链">
          <el-switch v-model="form.outerStatus" active-text="外链" inactive-text="内链" />
        </el-form-item>

        <el-form-item label="排序" prop="orderNo">
          <el-input-number v-model="form.orderNo" :min="0" :max="999" style="width: 100%" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="可见">
              <el-switch v-model="form.visible" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="actionDialogVisible"
      title="批量生成按钮权限"
      width="520px"
      destroy-on-close
    >
      <el-alert
        v-if="actionParent"
        :title="`将为「${actionParent.menuName}」生成按钮权限节点`"
        type="info"
        :closable="false"
        show-icon
        class="action-alert"
      />
      <el-checkbox-group v-model="selectedActions" class="action-grid">
        <el-checkbox v-for="item in actionOptions" :key="item.value" :value="item.value">
          {{ item.label }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionSaving" @click="handleBatchActions">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  Plus, Edit, Delete, Refresh, Search, FolderOpened, Menu, Tickets,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  queryMenuTree, createMenu, updateMenu, deleteMenu, batchCreateMenuActions,
  type MenuTreeNode, type MenuMutationPayload,
} from '@/api/modules/menu'

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const tableData = ref<MenuTreeNode[]>([])
const dialogVisible = ref(false)
const actionDialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const actionParent = ref<MenuTreeNode | null>(null)
const selectedActions = ref<string[]>([])
const actionSaving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<MenuMutationPayload & { parentId?: number | null }>({
  parentId: null,
  menuType: 'MENU',
  resourceKey: '',
  menuName: '',
  routeKey: null,
  grantKey: null,
  path: null,
  component: null,
  redirect: null,
  icon: null,
  orderNo: 0,
  visible: true,
  enabled: true,
  outerStatus: false,
  applicationKey: null,
})

const actionOptions = [
  { label: '查看', value: 'read' },
  { label: '新增', value: 'create' },
  { label: '修改', value: 'update' },
  { label: '删除', value: 'delete' },
  { label: '导出', value: 'export' },
  { label: '导入', value: 'import' },
]

const menuTypeOptions = [
  { label: '目录', value: 'DIR' },
  { label: '菜单', value: 'MENU' },
  { label: '按钮', value: 'BUTTON' },
  { label: 'API', value: 'API' },
]

const formRules: FormRules = {
  resourceKey: [{ required: true, message: '请输入资源标识', trigger: 'blur' }],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
}

// flatten tree for el-tree-select
function flattenForSelect(nodes: MenuTreeNode[]): MenuTreeNode[] {
  return nodes.flatMap((n) => [n, ...flattenForSelect(n.children || [])])
}

const menuTreeForSelect = computed(() => flattenForSelect(tableData.value))

const filteredTableData = computed(() => {
  if (!keyword.value) return tableData.value
  const kw = keyword.value.toLowerCase()
  const filterTree = (nodes: MenuTreeNode[]): MenuTreeNode[] =>
    nodes
      .map((n) => {
        const children = filterTree(n.children || [])
        const match =
          n.menuName.toLowerCase().includes(kw) ||
          n.resourceKey.toLowerCase().includes(kw) ||
          (n.routeKey && n.routeKey.toLowerCase().includes(kw)) ||
          (n.grantKey && n.grantKey.toLowerCase().includes(kw)) ||
          (n.applicationKey && n.applicationKey.toLowerCase().includes(kw))
        if (match || children.length > 0) {
          return { ...n, children }
        }
        return null
      })
      .filter(Boolean) as MenuTreeNode[]
  return filterTree(tableData.value)
})

const nodeTitleStyle = () => ({ paddingLeft: '0px' })

async function load() {
  loading.value = true
  try {
    tableData.value = await queryMenuTree()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载菜单失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.parentId = null
  form.menuType = 'MENU'
  form.resourceKey = ''
  form.menuName = ''
  form.routeKey = null
  form.grantKey = null
  form.path = null
  form.component = null
  form.redirect = null
  form.icon = null
  form.orderNo = 0
  form.visible = true
  form.enabled = true
  form.outerStatus = false
  form.applicationKey = null
  formRef.value?.resetFields()
}

function openCreate(parentId: number | null) {
  isEditing.value = false
  editingId.value = null
  resetForm()
  form.parentId = parentId
  if (parentId == null) {
    form.menuType = 'DIR'
  }
  dialogVisible.value = true
}

function openEdit(row: MenuTreeNode) {
  isEditing.value = true
  editingId.value = row.id
  form.parentId = row.parentId
  form.menuType = row.menuType
  form.resourceKey = row.resourceKey
  form.menuName = row.menuName
  form.routeKey = row.routeKey
  form.grantKey = row.grantKey
  form.path = row.path
  form.component = row.component
  form.redirect = row.redirect
  form.icon = row.icon
  form.orderNo = row.orderNo
  form.visible = row.visible
  form.enabled = row.enabled
  form.outerStatus = row.outerStatus
  form.applicationKey = row.applicationKey
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload: MenuMutationPayload = {
      parentId: form.parentId ?? null,
      menuType: form.menuType,
      resourceKey: form.resourceKey,
      menuName: form.menuName,
      routeKey: form.routeKey || null,
      grantKey: form.grantKey || null,
      path: form.path || null,
      component: form.component || null,
      redirect: form.redirect || null,
      icon: form.icon || null,
      orderNo: form.orderNo,
      visible: form.visible,
      enabled: form.enabled,
      outerStatus: form.outerStatus,
      applicationKey: form.applicationKey || null,
    }

    if (isEditing.value && editingId.value) {
      await updateMenu(editingId.value, payload)
      ElMessage.success('菜单已更新')
    } else {
      await createMenu(payload)
      ElMessage.success('菜单已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: MenuTreeNode) {
  try {
    await ElMessageBox.confirm(`确定要删除菜单「${row.menuName}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteMenu(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

async function copyGrantKey(row: MenuTreeNode) {
  if (!row.grantKey) {
    return
  }
  await navigator.clipboard?.writeText(row.grantKey)
  ElMessage.success('权限标识已复制')
}

function openBatchActions(row: MenuTreeNode) {
  actionParent.value = row
  selectedActions.value = ['read', 'create', 'update', 'delete']
  actionDialogVisible.value = true
}

async function handleBatchActions() {
  if (!actionParent.value || selectedActions.value.length === 0) {
    ElMessage.warning('请选择要生成的按钮权限')
    return
  }
  actionSaving.value = true
  try {
    await batchCreateMenuActions(actionParent.value.id, { actions: selectedActions.value })
    ElMessage.success('按钮权限已生成')
    actionDialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '生成失败')
  } finally {
    actionSaving.value = false
  }
}

function typeLabel(type: MenuTreeNode['menuType']) {
  const labels: Record<MenuTreeNode['menuType'], string> = {
    DIR: '目录',
    MENU: '菜单',
    BUTTON: '按钮',
    API: 'API',
  }
  return labels[type]
}

onMounted(load)
</script>

<style scoped>
.panel-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.dashboard-panel {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}
.resource-panel__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.eyebrow {
  font-size: 12px;
  color: #909399;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
h3 {
  margin: 4px 0 0;
  font-size: 18px;
}
.resource-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}
.resource-toolbar__search {
  width: 240px;
}
.node-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.node-icon {
  font-size: 16px;
  color: #409eff;
}
.node-label {
  font-weight: 500;
}
.route-key {
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}
.text-muted {
  color: #c0c4cc;
}
.sort-value {
  font-weight: 500;
}
.menu-permission-table :deep(.el-table__row) {
  cursor: default;
}
</style>