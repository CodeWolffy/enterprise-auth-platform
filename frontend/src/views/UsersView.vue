<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Users</span>
        <strong>{{ totalUsers }}</strong>
        <span>当前筛选条件下的用户总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Enabled</span>
        <strong>{{ enabledCount }}</strong>
        <span>处于启用状态的用户</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Disabled</span>
        <strong>{{ disabledCount }}</strong>
        <span>当前已禁用的用户</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Roles</span>
        <strong>{{ averageRoleCount }}</strong>
        <span>当前页人均角色数</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Users</span>
          <h3>用户管理</h3>
        </div>
        <el-button type="primary" @click="openUser()">新增用户</el-button>
      </div>

      <el-form :inline="true" class="toolbar-inline" @submit.prevent="doSearch">
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="按用户名搜索" clearable />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.mobile" placeholder="按手机号搜索" clearable />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="queryParams.email" placeholder="按邮箱搜索" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.enabled" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="doSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="users" stripe>
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="显示名称" min-width="140" />
        <el-table-column prop="mobile" label="手机号" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="200">
          <template #default="{ row }">{{ row.roles.join(' / ') || '-' }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="320">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openUser(row)">编辑</el-button>
            <el-button link type="primary" @click="openRoleAssignment(row)">分配角色</el-button>
            <el-button link type="warning" @click="promptResetPassword(row)">重置密码</el-button>
            <el-button link type="danger" @click="removeUser(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="totalUsers"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="用户详情" size="720px">
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户 ID">{{ detailData.id }}</el-descriptions-item>
          <el-descriptions-item label="租户">{{ detailData.tenantId }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ detailData.username }}</el-descriptions-item>
          <el-descriptions-item label="显示名称">{{ detailData.displayName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detailData.mobile || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detailData.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailData.enabled ? 'success' : 'info'">
              {{ detailData.enabled ? '启用' : '禁用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="数据权限">{{ detailData.dataScopeType }}</el-descriptions-item>
          <el-descriptions-item label="部门 ID">{{ detailData.deptId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ detailData.roles.join(', ') || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-tip">
          <el-alert
            title="当前详情会受数据权限约束，列表不可见的用户不会出现在本页面。"
            type="info"
            :closable="false"
          />
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="userVisible" :title="editingUserId ? '编辑用户' : '新增用户'" width="760px">
      <el-form ref="formRef" label-position="top" :model="userForm" :rules="userRules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="userForm.username" :disabled="Boolean(editingUserId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示名称">
              <el-input v-model="userForm.displayName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model="userForm.mobile" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="userForm.email" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="editingUserId ? '重置密码' : '初始密码'" prop="password">
              <el-input
                v-model="userForm.password"
                type="password"
                :placeholder="editingUserId ? '留空表示不修改密码' : '请输入初始密码'"
                show-password
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门 ID" prop="deptId">
              <el-input-number v-model="userForm.deptId" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
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
            <el-option
              v-for="role in roles"
              :key="role.code"
              :label="`${role.name} (${role.code})`"
              :value="role.code"
            />
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
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  assignUserRoles,
  createUser,
  deleteUser,
  queryAssignedRoles,
  queryRoles,
  queryUsers,
  updateUser,
} from '@/api/platform'
import type { RoleView, UserSummary } from '@/types/auth'

const users = ref<UserSummary[]>([])
const roles = ref<RoleView[]>([])
const totalUsers = ref(0)
const userVisible = ref(false)
const roleVisible = ref(false)
const detailVisible = ref(false)
const editingUserId = ref<number | null>(null)
const roleTargetUserId = ref<number | null>(null)
const detailData = ref<UserSummary | null>(null)
const selectedRoleCodes = ref<string[]>([])
const loading = ref(false)
const formRef = ref<FormInstance>()

const queryParams = reactive({
  username: '',
  mobile: '',
  email: '',
  enabled: undefined as boolean | undefined,
  page: 1,
  size: 10,
})

const userForm = reactive({
  username: '',
  displayName: '',
  mobile: '',
  email: '',
  password: '',
  deptId: 1 as number | null,
  enabled: true,
})

const userRules = reactive<FormRules>({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  mobile: [
    {
      pattern: /^1\d{10}$/,
      message: '请输入有效的 11 位手机号',
      trigger: ['blur', 'change'],
    },
  ],
  email: [{ type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] }],
  password: [
    {
      validator: (_rule, value, callback) => {
        if (!editingUserId.value && !value) {
          callback(new Error('请输入初始密码'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  deptId: [
    {
      validator: (_rule, value, callback) => {
        if (value == null || value < 1) {
          callback(new Error('请输入有效的部门 ID'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
})

const enabledCount = computed(() => users.value.filter((item) => item.enabled).length)
const disabledCount = computed(() => users.value.filter((item) => !item.enabled).length)
const averageRoleCount = computed(() => {
  if (!users.value.length) {
    return '0.0'
  }
  const total = users.value.reduce((sum, item) => sum + item.roles.length, 0)
  return (total / users.value.length).toFixed(1)
})

void load()

async function load() {
  loading.value = true
  try {
    const [userPage, roleList] = await Promise.all([queryUsers(queryParams), queryRoles()])
    users.value = userPage.records
    totalUsers.value = userPage.total
    roles.value = roleList
  } finally {
    loading.value = false
  }
}

function doSearch() {
  queryParams.page = 1
  void load()
}

function resetSearch() {
  queryParams.username = ''
  queryParams.mobile = ''
  queryParams.email = ''
  queryParams.enabled = undefined
  queryParams.page = 1
  void load()
}

function openDetail(row: UserSummary) {
  detailData.value = row
  detailVisible.value = true
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
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()

  const payload = {
    username: userForm.username,
    displayName: userForm.displayName || null,
    mobile: userForm.mobile || null,
    email: userForm.email || null,
    password: userForm.password || undefined,
    deptId: userForm.deptId,
    enabled: userForm.enabled,
    roleCodes: [],
  }

  if (editingUserId.value) {
    await updateUser(editingUserId.value, payload)
    ElMessage.success('用户已更新')
  } else {
    await createUser({
      ...payload,
      password: userForm.password,
    })
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

function promptResetPassword(row: UserSummary) {
  ElMessageBox.prompt('请输入新密码', '重置密码', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputType: 'password',
    inputPattern: /^.{8,64}$/,
    inputErrorMessage: '密码长度需要在 8 到 64 位之间',
  })
    .then(async ({ value }) => {
      await updateUser(row.id, {
        displayName: row.displayName || null,
        mobile: row.mobile || null,
        email: row.email || null,
        deptId: row.deptId || null,
        enabled: row.enabled,
        password: value,
      })
      ElMessage.success('密码重置成功')
    })
    .catch(() => {})
}

async function removeUser(id: number) {
  await ElMessageBox.confirm('删除用户后将无法继续登录，是否继续？', '删除确认', { type: 'warning' })
  await deleteUser(id)
  ElMessage.success('用户已删除')
  await load()
}
</script>

<style scoped lang="scss">
.detail-tip {
  margin-top: 20px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
