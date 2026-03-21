<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Permissions</span>
        <strong>{{ permissions.length }}</strong>
        <span>当前租户下的权限总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Resources</span>
        <strong>{{ resourceCount }}</strong>
        <span>已登记的资源编码数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Actions</span>
        <strong>{{ actionCount }}</strong>
        <span>已登记的动作编码数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Scopes</span>
        <strong>{{ scopeCount }}</strong>
        <span>已登记的作用域数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Permissions</span>
          <h3>权限管理</h3>
        </div>
        <el-button type="primary" @click="openPermission()">新增权限</el-button>
      </div>

      <div class="table-tools">
        <el-radio-group v-model="permissionTablePrefs.density" size="small">
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
              v-for="item in permissionTablePrefs.columns"
              :key="item.key"
              :model-value="permissionTablePrefs.visibleColumnMap[item.key]"
              @change="(value: boolean) => permissionTablePrefs.setColumnVisible(item.key, value)"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </el-popover>
        <el-button size="small" @click="permissionTablePrefs.reset()">恢复默认</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="permissions"
        stripe
        :class="`table-density-${permissionTablePrefs.density}`"
        @header-dragend="onPermissionHeaderDragEnd"
      >
        <el-table-column
          v-if="permissionTablePrefs.visibleColumnMap.permissionName"
          column-key="permissionName"
          prop="permissionName"
          label="权限名称"
          min-width="180"
          :width="permissionTablePrefs.getColumnWidth('permissionName')"
        />
        <el-table-column
          v-if="permissionTablePrefs.visibleColumnMap.permissionCode"
          column-key="permissionCode"
          prop="permissionCode"
          label="权限编码"
          min-width="180"
          :width="permissionTablePrefs.getColumnWidth('permissionCode')"
        />
        <el-table-column
          v-if="permissionTablePrefs.visibleColumnMap.resourceCode"
          column-key="resourceCode"
          prop="resourceCode"
          label="资源编码"
          min-width="140"
          :width="permissionTablePrefs.getColumnWidth('resourceCode')"
        />
        <el-table-column
          v-if="permissionTablePrefs.visibleColumnMap.actionCode"
          column-key="actionCode"
          prop="actionCode"
          label="动作编码"
          min-width="140"
          :width="permissionTablePrefs.getColumnWidth('actionCode')"
        />
        <el-table-column
          v-if="permissionTablePrefs.visibleColumnMap.scopeCode"
          column-key="scopeCode"
          prop="scopeCode"
          label="作用域编码"
          min-width="140"
          :width="permissionTablePrefs.getColumnWidth('scopeCode')"
        />
        <el-table-column
          v-if="permissionTablePrefs.visibleColumnMap.actions"
          column-key="actions"
          fixed="right"
          label="操作"
          :width="permissionTablePrefs.getColumnWidth('actions') || 160"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="openPermission(row)">编辑</el-button>
            <el-button link type="danger" @click="removePermission(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑权限' : '新增权限'" width="680px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="权限名称" prop="permissionName">
              <el-input v-model="form.permissionName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权限编码" prop="permissionCode">
              <el-input v-model="form.permissionCode" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="资源编码" prop="resourceCode">
              <el-input v-model="form.resourceCode" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="动作编码" prop="actionCode">
              <el-input v-model="form.actionCode" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="作用域编码" prop="scopeCode">
              <el-input v-model="form.scopeCode" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createPermission, deletePermission, queryPermissions, updatePermission } from '@/api/platform'
import { useTablePreferences } from '@/composables/useTablePreferences'
import type { PermissionView } from '@/types/auth'

const permissions = ref<PermissionView[]>([])
const loading = ref(false)
const visible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const permissionTablePrefs = useTablePreferences('table:permissions', [
  { key: 'permissionName', label: '权限名称', width: 180 },
  { key: 'permissionCode', label: '权限编码', width: 180 },
  { key: 'resourceCode', label: '资源编码', width: 140 },
  { key: 'actionCode', label: '动作编码', width: 140 },
  { key: 'scopeCode', label: '作用域编码', width: 140 },
  { key: 'actions', label: '操作', width: 160 },
])

const form = reactive({
  resourceCode: '',
  actionCode: '',
  scopeCode: '',
  permissionName: '',
  permissionCode: '',
})

const rules = reactive<FormRules>({
  permissionName: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  permissionCode: [{ required: true, message: '请输入权限编码', trigger: 'blur' }],
  resourceCode: [{ required: true, message: '请输入资源编码', trigger: 'blur' }],
  actionCode: [{ required: true, message: '请输入动作编码', trigger: 'blur' }],
  scopeCode: [{ required: true, message: '请输入作用域编码', trigger: 'blur' }],
})

const resourceCount = computed(() => new Set(permissions.value.map((item) => item.resourceCode)).size)
const actionCount = computed(() => new Set(permissions.value.map((item) => item.actionCode)).size)
const scopeCount = computed(() => new Set(permissions.value.map((item) => item.scopeCode)).size)

void load()

async function load() {
  loading.value = true
  try {
    permissions.value = await queryPermissions()
  } finally {
    loading.value = false
  }
}

function openPermission(row?: PermissionView) {
  editingId.value = row?.id ?? null
  Object.assign(form, {
    resourceCode: row?.resourceCode ?? '',
    actionCode: row?.actionCode ?? '',
    scopeCode: row?.scopeCode ?? '',
    permissionName: row?.permissionName ?? '',
    permissionCode: row?.permissionCode ?? '',
  })
  visible.value = true
}

async function submit() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()

  const payload = {
    resourceCode: form.resourceCode,
    actionCode: form.actionCode,
    scopeCode: form.scopeCode,
    permissionName: form.permissionName || null,
    permissionCode: form.permissionCode,
  }
  if (editingId.value) {
    await updatePermission(editingId.value, payload)
    ElMessage.success('权限已更新')
  } else {
    await createPermission(payload)
    ElMessage.success('权限已创建')
  }
  visible.value = false
  await load()
}

async function removePermission(id: number) {
  await ElMessageBox.confirm('删除权限后可能影响角色授权，是否继续？', '删除确认', { type: 'warning' })
  await deletePermission(id)
  ElMessage.success('权限已删除')
  await load()
}

function onPermissionHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  permissionTablePrefs.setColumnWidth(key, newWidth)
}
</script>

<style scoped lang="scss">
.table-tools {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  margin: -4px 0 10px;
}

.column-chooser {
  display: grid;
  gap: 8px;
  max-height: 280px;
  overflow: auto;
}
</style>
