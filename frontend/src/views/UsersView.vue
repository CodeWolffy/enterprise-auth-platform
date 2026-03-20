<template>
  <div class="panel-stack">
    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Users</span>
          <h3>用户管理</h3>
        </div>
        <el-button type="primary" @click="openUser()">新增用户</el-button>
      </div>

      <el-table :data="users" stripe>
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="显示名称" min-width="140" />
        <el-table-column prop="mobile" label="手机号" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">{{ row.roles.join(' / ') || '-' }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click="openUser(row)">编辑</el-button>
            <el-button link type="primary" @click="openRoleAssignment(row)">分配角色</el-button>
            <el-button link type="danger" @click="removeUser(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="userVisible" :title="editingUserId ? '编辑用户' : '新增用户'" width="760px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="用户名"><el-input v-model="userForm.username" :disabled="Boolean(editingUserId)" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="显示名称"><el-input v-model="userForm.displayName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="手机号"><el-input v-model="userForm.mobile" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="邮箱"><el-input v-model="userForm.email" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="初始密码"><el-input v-model="userForm.password" type="password" placeholder="编辑时留空表示不改密码" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="部门 ID"><el-input-number v-model="userForm.deptId" :min="1" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="启用状态">
          <el-switch v-model="userForm.enabled" inline-prompt active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配用户角色" width="620px">
      <el-form label-position="top">
        <el-form-item label="角色集合">
          <el-select v-model="selectedRoleCodes" multiple style="width: 100%">
            <el-option v-for="role in roles" :key="role.code" :label="`${role.name} (${role.code})`" :value="role.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRoleAssignment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignUserRoles, createUser, deleteUser, queryAssignedRoles, queryRoles, queryUsers, updateUser } from '@/api/platform'
import type { RoleView, UserSummary } from '@/types/auth'

const users = ref<UserSummary[]>([])
const roles = ref<RoleView[]>([])
const userVisible = ref(false)
const roleVisible = ref(false)
const editingUserId = ref<number | null>(null)
const roleTargetUserId = ref<number | null>(null)
const selectedRoleCodes = ref<string[]>([])
const userForm = reactive({
  username: '',
  displayName: '',
  mobile: '',
  email: '',
  password: '',
  deptId: 1 as number | null,
  enabled: true,
})

void load()

async function load() {
  const [userList, roleList] = await Promise.all([queryUsers(), queryRoles()])
  users.value = userList
  roles.value = roleList
}

function openUser(row?: UserSummary) {
  editingUserId.value = row?.id ?? null
  Object.assign(userForm, {
    username: row?.username ?? '',
    displayName: row?.displayName ?? '',
    mobile: row?.mobile ?? '',
    email: row?.email ?? '',
    password: '',
    deptId: row?.deptId ?? 1,
    enabled: row?.enabled ?? true,
  })
  userVisible.value = true
}

async function submitUser() {
  const payload = {
    username: userForm.username,
    displayName: userForm.displayName || null,
    mobile: userForm.mobile || null,
    email: userForm.email || null,
    password: userForm.password || 'Admin@123456',
    deptId: userForm.deptId,
    enabled: userForm.enabled,
    roleCodes: [],
  }
  if (editingUserId.value) {
    await updateUser(editingUserId.value, payload)
    ElMessage.success('用户已更新')
  } else {
    await createUser(payload)
    ElMessage.success('用户已创建')
  }
  userVisible.value = false
  await load()
}

async function openRoleAssignment(row: UserSummary) {
  roleTargetUserId.value = row.id
  const assignedRoles = await queryAssignedRoles(row.id)
  selectedRoleCodes.value = assignedRoles.map((item) => item.code)
  roleVisible.value = true
}

async function submitRoleAssignment() {
  if (!roleTargetUserId.value) {
    return
  }
  await assignUserRoles(roleTargetUserId.value, selectedRoleCodes.value)
  roleVisible.value = false
  ElMessage.success('用户角色已更新')
  await load()
}

async function removeUser(id: number) {
  await ElMessageBox.confirm('删除用户后将无法继续登录，是否继续？', '删除确认', { type: 'warning' })
  await deleteUser(id)
  ElMessage.success('用户已删除')
  await load()
}
</script>
