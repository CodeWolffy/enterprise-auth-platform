<template>
  <div class="panel-stack menu-management-page">
    <section v-loading="loading" class="dashboard-panel menu-stage">
      <div class="menu-stage__toolbar">
        <el-button
          v-permission="'upms:sysmenu:add'"
          type="primary"
          :icon="Plus"
          @click="openCreate(null)"
        >
          新增菜单
        </el-button>
      </div>

      <div class="menu-sheet">
        <el-table
          :data="filteredTableData"
          height="100%"
          row-key="id"
          default-expand-all
          :tree-props="{ children: 'children' }"
          :indent="28"
          class="menu-permission-table"
          empty-text="暂无菜单数据"
        >
          <el-table-column label="菜单名称" min-width="300">
            <template #default="{ row }">
              <div class="node-title" :style="nodeTitleStyle(row)">
                <span class="node-icon-wrap" :class="nodeIconClass(row)">
                  <el-icon>
                    <Lock v-if="row.type === '1'" />
                    <component :is="resolveMenuIcon(row)" v-else />
                  </el-icon>
                </span>
                <span class="node-label">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="菜单权限" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <code v-if="row.permission" class="mono-value">{{ row.permission }}</code>
              <span v-else class="empty-value">—</span>
            </template>
          </el-table-column>

          <el-table-column label="菜单编码" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <code v-if="row.component" class="mono-value">{{ row.component }}</code>
              <span v-else class="empty-value">—</span>
            </template>
          </el-table-column>

          <el-table-column label="菜单路径" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <code v-if="row.path" class="mono-value">{{ row.path }}</code>
              <span v-else class="empty-value">—</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="190" fixed="right" align="right" header-align="center">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button
                  v-permission="'upms:sysmenu:edit'"
                  link
                  type="primary"
                  size="small"
                  @click="openEdit(row)"
                >
                  修改菜单
                </el-button>
                <el-button
                  v-permission="'upms:sysmenu:del'"
                  link
                  type="primary"
                  size="small"
                  :disabled="false"
                  @click="handleDelete(row)"
                >
                  删除菜单
                </el-button>
                <el-button
                  v-permission="'upms:sysmenu:add'"
                  link
                  type="primary"
                  size="small"
                  @click="openCreate(row.id)"
                >
                  新增下级
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      class="menu-dialog"
      :title="isEditing ? '编辑菜单' : '新增菜单'"
      width="640px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="92px"
        label-position="right"
      >
        <div class="dialog-grid">
          <el-form-item v-if="!isEditing" label="上级节点" class="dialog-grid__full">
            <el-tree-select
              v-model="form.parentId"
              :data="menuTreeForSelect"
              :props="{ label: 'name', value: 'id', children: 'children' }"
              check-strictly
              clearable
              placeholder="留空为顶层节点"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="菜单类型" prop="type" class="dialog-grid__full">
            <el-segmented v-model="form.type" :options="typeOptions" />
          </el-form-item>

          <el-form-item label="菜单名称" prop="name">
            <el-input v-model="form.name" placeholder="输入菜单名称" maxlength="60" />
          </el-form-item>

          <el-form-item label="应用标识">
            <el-input v-model="form.applicationKey" placeholder="用于套餐/能力映射，如 app_base" clearable />
          </el-form-item>

          <el-form-item v-if="form.type === '1'" label="菜单权限" prop="permission">
            <el-input v-model="form.permission" placeholder="如 upms:sysmenu:add" />
          </el-form-item>

          <el-form-item v-if="form.type === '0'" label="菜单路径" prop="path">
            <el-input v-model="form.path" placeholder="/platform/menu" />
          </el-form-item>

          <el-form-item v-if="form.type === '0'" label="菜单编码" prop="component" class="dialog-grid__full">
            <el-input v-model="form.component" placeholder="upms/menu/index 或 MenuManagementView" />
          </el-form-item>

          <el-form-item v-if="form.type === '0'" label="重定向">
            <el-input v-model="form.redirect" placeholder="重定向路径（可选）" />
          </el-form-item>

          <el-form-item v-if="form.type === '0'" label="图标">
            <el-select
              v-model="form.icon"
              clearable
              filterable
              placeholder="选择菜单图标"
              style="width: 100%"
            >
              <el-option
                v-for="item in iconOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              >
                <div class="icon-option">
                  <el-icon><component :is="resolveAppIcon(item.value)" /></el-icon>
                  <span>{{ item.label }}</span>
                </div>
              </el-option>
              <template #prefix>
                <el-icon v-if="form.icon"><component :is="resolveAppIcon(form.icon)" /></el-icon>
              </template>
            </el-select>
          </el-form-item>

          <el-form-item v-if="form.type === '0'" label="外链">
            <el-switch v-model="form.outerStatus" active-text="外链" inactive-text="内链" />
          </el-form-item>

          <el-form-item label="排序" prop="sort">
            <el-input-number v-model="form.sort" :min="0" :max="999" style="width: 100%" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Plus, Lock,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { APP_ICON_OPTIONS, resolveAppIcon } from '@/app/registry/module-manifest'
import {
  createMenu,
  deleteMenu,
  queryMenuTree,
  updateMenu,
  type MenuMutationPayload,
  type MenuTreeNode,
} from '@/api/modules/menu'

const loading = ref(false)
const saving = ref(false)
const tableData = ref<MenuTreeNode[]>([])
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<MenuMutationPayload & { parentId?: number | null }>({
  parentId: null,
  type: '0',
  name: '',
  permission: null,
  path: null,
  component: null,
  redirect: null,
  icon: null,
  sort: 0,
  outerStatus: false,
  applicationKey: null,
})

const typeOptions = [
  { label: '菜单', value: '0' },
  { label: '按钮', value: '1' },
]

const formRules: FormRules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
}

const menuTreeForSelect = computed(() => tableData.value)
const iconOptions = APP_ICON_OPTIONS

const filteredTableData = computed(() => tableData.value)

function resolveMenuIcon(row: MenuTreeNode) {
  return resolveAppIcon(row.icon)
}

function nodeTitleStyle() {
  return { paddingLeft: '0' }
}

function nodeIconClass(row: MenuTreeNode) {
  if (row.type === '1') {
    return 'node-icon-wrap--button'
  }
  if (row.children?.length) {
    return 'node-icon-wrap--folder'
  }
  return 'node-icon-wrap--menu'
}

async function load() {
  loading.value = true
  try {
    const data = await queryMenuTree()
    computeDepth(data)
    tableData.value = data
  } catch (error: any) {
    ElMessage.error(error?.message || '加载菜单失败')
  } finally {
    loading.value = false
  }
}

function computeDepth(nodes: MenuTreeNode[], depth = 0) {
  for (const node of nodes) {
    ;(node as MenuTreeNode & { _depth?: number })._depth = depth
    if (node.children?.length) {
      computeDepth(node.children, depth + 1)
    }
  }
}

function resetForm() {
  form.parentId = null
  form.type = '0'
  form.name = ''
  form.permission = null
  form.path = null
  form.component = null
  form.redirect = null
  form.icon = null
  form.sort = 0
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
    form.type = '0'
  }
  dialogVisible.value = true
}

function openEdit(row: MenuTreeNode) {
  isEditing.value = true
  editingId.value = row.id
  form.parentId = row.parentId
  form.type = row.type
  form.name = row.name
  form.permission = row.permission
  form.path = row.path
  form.component = row.component
  form.redirect = row.redirect
  form.icon = row.icon
  form.sort = row.sort
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
      type: form.type,
      name: form.name,
      permission: form.type === '1' ? form.permission || null : null,
      path: form.type === '0' ? form.path || null : null,
      component: form.type === '0' ? form.component || null : null,
      redirect: form.type === '0' ? form.redirect || null : null,
      icon: form.type === '0' ? form.icon || null : null,
      sort: form.sort,
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
  } catch (error: any) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: MenuTreeNode) {
  try {
    await ElMessageBox.confirm(`确定要删除菜单「${row.name}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await deleteMenu(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.message || '删除失败')
    }
  }
}

onMounted(load)
</script>

<style scoped>
.menu-management-page {
  height: calc(100vh - 170px);
  min-height: 620px;
  gap: 16px;
}

.menu-stage {
  height: 100%;
  padding: 8px;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.menu-stage__toolbar {
  position: relative;
  z-index: 2;
  flex: none;
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
  background: #fff;
}

.menu-sheet {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1px solid #ebeef5;
  border-radius: 2px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(31, 45, 61, 0.04);
}

.menu-permission-table {
  width: 100%;
  height: 100%;
  color: #606266;
}

.menu-permission-table :deep(.el-table__header-wrapper) {
  position: sticky;
  top: 0;
  z-index: 3;
}

.menu-permission-table :deep(.el-table__body-wrapper) {
  overflow-y: auto;
}

.menu-permission-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.menu-permission-table :deep(th.el-table__cell) {
  height: 36px;
  background: #f5f7fa;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid #ebeef5;
}

.menu-permission-table :deep(td.el-table__cell) {
  padding: 5px 0;
  border-bottom: 1px solid #ebeef5;
}

.menu-permission-table :deep(.cell) {
  line-height: 24px;
  font-size: 14px;
}

.menu-permission-table :deep(.el-table__row:hover > td.el-table__cell) {
  background: #f9fbff;
}

.menu-permission-table :deep(.el-table__fixed-right::before),
.menu-permission-table :deep(.el-table__fixed::before) {
  display: none;
}

.menu-permission-table :deep(.el-table__row .el-table__cell:first-child .cell) {
  display: flex;
  align-items: center;
}

.menu-permission-table :deep(.el-table__expand-icon),
.menu-permission-table :deep(.el-table__placeholder) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 26px;
  margin-right: 2px;
  line-height: 26px;
  flex: none;
}

.menu-permission-table :deep(.el-table__expand-icon .el-icon) {
  font-size: 14px;
}

.node-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 26px;
}

.node-icon-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 21px;
  height: 21px;
  color: #409eff;
  font-size: 18px;
  flex: none;
}

.node-icon-wrap--folder,
.node-icon-wrap--menu {
  color: #409eff;
  background: transparent;
}

.node-icon-wrap--button {
  color: #909399;
  background: transparent;
}

.node-label {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.mono-value {
  display: inline;
  max-width: 100%;
  padding: 0;
  border-radius: 0;
  background: transparent;
  color: #606266;
  font-size: 14px;
  line-height: 1.4;
  border: 0;
  font-family: inherit;
}

.empty-value {
  color: #c0c4cc;
}

.table-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: nowrap;
  gap: 14px;
}

.table-actions :deep(.el-button) {
  margin-left: 0;
  padding: 0;
  font-size: 14px;
}

.table-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 14px;
}

.dialog-grid__full {
  grid-column: 1 / -1;
}

.icon-option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.icon-option .el-icon {
  color: #409eff;
}

:deep(.menu-dialog .el-dialog) {
  border-radius: 20px;
  overflow: hidden;
}

:deep(.menu-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 18px 22px 16px;
  border-bottom: 1px solid #eef2f8;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

:deep(.menu-dialog .el-dialog__body) {
  padding: 20px 22px 12px;
}

:deep(.menu-dialog .el-dialog__footer) {
  padding: 12px 22px 20px;
}

@media (max-width: 900px) {
  .dialog-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .dialog-grid__full {
    grid-column: auto;
  }
}
</style>