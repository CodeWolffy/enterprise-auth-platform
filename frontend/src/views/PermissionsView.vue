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

      <el-table :data="permissions" stripe>
        <el-table-column prop="permissionName" label="权限名称" min-width="180" />
        <el-table-column prop="permissionCode" label="权限编码" min-width="180" />
        <el-table-column prop="resourceCode" label="资源编码" min-width="120" />
        <el-table-column prop="actionCode" label="动作编码" min-width="120" />
        <el-table-column prop="scopeCode" label="作用域编码" min-width="120" />
        <el-table-column fixed="right" label="操作" width="160">
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
import type { PermissionView } from '@/types/auth'

const permissions = ref<PermissionView[]>([])
const visible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

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
  permissions.value = await queryPermissions()
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
</script>
