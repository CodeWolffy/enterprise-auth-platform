<template>
  <div class="panel-stack">
    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Roles</span>
          <h3>角色管理</h3>
        </div>
        <el-button type="primary" @click="openRole()">新增角色</el-button>
      </div>

      <el-table :data="roles" stripe>
        <el-table-column prop="code" label="角色编码" min-width="140" />
        <el-table-column prop="name" label="角色名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="220" />
        <el-table-column prop="dataScopeType" label="数据权限" min-width="120" />
        <el-table-column fixed="right" label="操作" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRole(row)">编辑</el-button>
            <el-button link type="primary" @click="openPermissionAssignment(row)">分配权限</el-button>
            <el-button link type="danger" @click="removeRole(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="roleVisible" :title="editingRoleId ? '编辑角色' : '新增角色'" width="640px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" :disabled="Boolean(editingRoleId)" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="角色名称"><el-input v-model="roleForm.roleName" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="角色描述"><el-input v-model="roleForm.roleDesc" /></el-form-item>
        <el-form-item label="数据权限范围">
          <el-select v-model="roleForm.dataScopeType" style="width: 100%">
            <el-option label="全部" value="ALL" />
            <el-option label="本人" value="SELF" />
            <el-option label="本部门" value="DEPT" />
            <el-option label="本部门及子部门" value="DEPT_AND_CHILDREN" />
            <el-option label="自定义范围" value="CUSTOM" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRole">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionVisible" title="分配角色权限" width="760px">
      <el-form label-position="top">
        <el-form-item label="权限集合">
          <el-select v-model="selectedPermissionCodes" multiple style="width: 100%">
            <el-option
              v-for="permission in permissions"
              :key="permission.permissionCode"
              :label="`${permission.permissionName ?? permission.permissionCode} (${permission.permissionCode})`"
              :value="permission.permissionCode"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="permissionVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPermissionAssignment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignRolePermissions,
  createRole,
  deleteRole,
  queryAssignedPermissions,
  queryPermissions,
  queryRoles,
  updateRole,
} from '@/api/platform'
import type { PermissionView, RoleView } from '@/types/auth'

const roles = ref<RoleView[]>([])
const permissions = ref<PermissionView[]>([])
const roleVisible = ref(false)
const permissionVisible = ref(false)
const editingRoleId = ref<number | null>(null)
const permissionTargetRoleId = ref<number | null>(null)
const selectedPermissionCodes = ref<string[]>([])
const roleForm = reactive({
  roleCode: '',
  roleName: '',
  roleDesc: '',
  dataScopeType: 'ALL',
})

void load()

async function load() {
  const [roleList, permissionList] = await Promise.all([queryRoles(), queryPermissions()])
  roles.value = roleList
  permissions.value = permissionList
}

function openRole(row?: RoleView) {
  editingRoleId.value = row?.id ?? null
  Object.assign(roleForm, {
    roleCode: row?.code ?? '',
    roleName: row?.name ?? '',
    roleDesc: row?.description ?? '',
    dataScopeType: row?.dataScopeType ?? 'ALL',
  })
  roleVisible.value = true
}

async function submitRole() {
  const payload = {
    roleCode: roleForm.roleCode,
    roleName: roleForm.roleName,
    roleDesc: roleForm.roleDesc || null,
    dataScopeType: roleForm.dataScopeType,
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

async function openPermissionAssignment(row: RoleView) {
  permissionTargetRoleId.value = row.id
  const assigned = await queryAssignedPermissions(row.id)
  selectedPermissionCodes.value = assigned.map((item) => item.permissionCode)
  permissionVisible.value = true
}

async function submitPermissionAssignment() {
  if (!permissionTargetRoleId.value) {
    return
  }
  await assignRolePermissions(permissionTargetRoleId.value, selectedPermissionCodes.value)
  permissionVisible.value = false
  ElMessage.success('角色权限已更新')
  await load()
}

async function removeRole(id: number) {
  await ElMessageBox.confirm('删除角色后原有授权关系会失效，是否继续？', '删除确认', { type: 'warning' })
  await deleteRole(id)
  ElMessage.success('角色已删除')
  await load()
}
</script>
