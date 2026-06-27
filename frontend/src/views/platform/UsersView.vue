<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">用户</span>
        <strong>{{ totalUsers }}</strong>
        <span>当前筛选条件下的用户总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">启用</span>
        <strong>{{ enabledCount }}</strong>
        <span>处于启用状态的用户</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">禁用</span>
        <strong>{{ disabledCount }}</strong>
        <span>当前已禁用的用户</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">角色</span>
        <strong>{{ averageRoleCount }}</strong>
        <span>当前页人均角色数</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>

          <span class="eyebrow">用户</span>
          <h3>用户管理</h3>
        </div>
        <el-button v-permission="'upms:sysuser:add'" type="primary" data-testid="users-create" @click="openUser()">新增用户</el-button>
      </div>

      <AdvancedSearch @search="doSearch" @reset="resetSearch">
        <el-form-item v-if="isPlatformAdmin" label="租户">
          <el-select
            v-model="queryParams.tenantId"
            placeholder="全部租户"
            clearable
            filterable
            style="width: 220px"
            @change="handleQueryTenantChange"
          >
            <el-option v-for="tenant in tenantOptions" :key="tenant.tenantId" :label="tenant.label" :value="tenant.tenantId" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="queryParams.username" placeholder="按用户名搜索" clearable />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="queryParams.mobile" placeholder="按手机号搜索" clearable />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="queryParams.email" placeholder="按邮箱搜索" clearable />
        </el-form-item>
        <el-form-item label="部门">
          <el-tree-select
            v-model="queryDeptKey"
            :data="queryDepartmentTree"
            clearable
            check-strictly
            node-key="key"
            :props="{ label: 'label', value: 'key', children: 'children' }"
            placeholder="按部门筛选"
            style="width: 220px"
            @change="handleQueryDeptChange"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.enabled" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
      </AdvancedSearch>

      <div class="table-tools">
        <el-radio-group v-model="userTablePrefs.density" size="small">
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
              v-for="item in userTablePrefs.columns"
              :key="item.key"
              :model-value="userTablePrefs.visibleColumnMap[item.key]"
              @change="(value: boolean) => userTablePrefs.setColumnVisible(item.key, value)"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </el-popover>
        <el-button size="small" @click="userTablePrefs.reset()">恢复默认</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="users"
        stripe
        :class="`table-density-${userTablePrefs.density}`"
        @header-dragend="onUserHeaderDragEnd"
      >
        <el-table-column
          v-if="userTablePrefs.visibleColumnMap.tenantId"
          column-key="tenantId"
          prop="tenantId"
          label="租户"
          min-width="130"
          :width="userTablePrefs.getColumnWidth('tenantId')"
        />
        <el-table-column
          v-if="userTablePrefs.visibleColumnMap.username"
          column-key="username"
          prop="username"
          label="用户名"
          min-width="140"
          :width="userTablePrefs.getColumnWidth('username')"
        />
        <el-table-column
          v-if="userTablePrefs.visibleColumnMap.displayName"
          column-key="displayName"
          prop="displayName"
          label="显示名称"
          min-width="140"
          :width="userTablePrefs.getColumnWidth('displayName')"
        />
        <el-table-column
          v-if="userTablePrefs.visibleColumnMap.mobile"
          column-key="mobile"
          prop="mobile"
          label="手机号"
          min-width="140"
          :width="userTablePrefs.getColumnWidth('mobile')"
        />
        <el-table-column
          v-if="userTablePrefs.visibleColumnMap.email"
          column-key="email"
          prop="email"
          label="邮箱"
          min-width="200"
          :width="userTablePrefs.getColumnWidth('email')"
        />
        <el-table-column
          v-if="userTablePrefs.visibleColumnMap.enabled"
          column-key="enabled"
          label="状态"
          min-width="100"
          :width="userTablePrefs.getColumnWidth('enabled')"
        >
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="userTablePrefs.visibleColumnMap.roles"
          column-key="roles"
          label="角色"
          min-width="200"
          :width="userTablePrefs.getColumnWidth('roles')"
        >
          <template #default="{ row }">{{ row.roles.join(' / ') || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="userTablePrefs.visibleColumnMap.actions" column-key="actions" fixed="right" label="操作" width="320">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-permission="'upms:sysuser:edit'" link type="primary" data-testid="users-edit" @click="openUser(row)">编辑</el-button>
            <el-button v-permission="'upms:sysuser:edit'" link type="primary" @click="openRoleAssignment(row)">分配角色</el-button>
            <el-button v-permission="'upms:sysuser:edit'" link type="warning" @click="promptResetPassword(row)">重置密码</el-button>
            <el-button v-permission="'upms:sysuser:del'" link type="danger" data-testid="users-delete" @click="removeUser(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户数据" />
        </template>
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
        <el-descriptions :column="2" border class="drawer-section drawer-section--overview">
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

        <div class="detail-tip drawer-section drawer-section--guide">
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
        <el-row v-if="isPlatformAdmin" :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属租户">
              <el-select
                v-model="userFormTenantId"
                :disabled="Boolean(editingUserId)"
                filterable
                style="width: 100%"
                @change="handleUserTenantChange"
              >
                <el-option v-for="tenant in tenantOptions" :key="tenant.tenantId" :label="tenant.label" :value="tenant.tenantId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
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
            <el-form-item label="所属部门" prop="deptId">
              <el-tree-select
                v-model="userForm.deptId"
                :data="userDepartmentTree"
                check-strictly
                node-key="id"
                :props="{ label: 'label', value: 'id', children: 'children' }"
                placeholder="请选择部门"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="角色" prop="roleCodes">
          <el-select v-model="userForm.roleCodes" multiple style="width: 100%" placeholder="请选择至少一个角色" :disabled="!userFormTenantId">
            <el-option
              v-for="role in userAssignableRoles"
              :key="roleKey(role)"
              :label="`${role.name} (${role.code})`"
              :value="role.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="userForm.enabled" inline-prompt active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userVisible = false">取消</el-button>
        <el-button v-permission="['upms:sysuser:add', 'upms:sysuser:edit']" type="primary" @click="submitUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配用户角色" width="620px">
      <el-form label-position="top">
        <el-form-item label="角色集合">
          <el-select v-model="selectedRoleCodes" multiple style="width: 100%">
            <el-option
              v-for="role in roleAssignmentRoles"
              :key="roleKey(role)"
              :label="`${role.name} (${role.code})`"
              :value="role.code"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button v-permission="'upms:sysuser:edit'" type="primary" @click="submitRoleAssignment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  assignUserRoles,
  createUser,
  deleteUser,
  queryAssignedRoles,
  queryDepartments,
  queryPasswordPolicy,
  queryRoleOptions,
  queryTenants,
  queryUsers,
  updateUser,
  type SecurityPasswordPolicy,
} from '@/api/modules'
import { useTablePreferences } from '@/composables/useTablePreferences'
import type { RoleView } from '@/types/role'
import type { DepartmentView } from '@/types/dept'
import type { TenantView } from '@/types/tenant'
import type { UserSummary } from '@/types/user'
import { useAuthStore } from '@/stores/auth'

type DepartmentTreeNode = {
  id: number
  key: string
  label: string
  tenantId: string
  parentId: number
  children?: DepartmentTreeNode[]
}

type TenantOption = {
  tenantId: string
  label: string
}

const users = ref<UserSummary[]>([])
const roles = ref<RoleView[]>([])
const departments = ref<DepartmentView[]>([])
const tenantCatalog = ref<TenantView[]>([])
const authStore = useAuthStore()
const totalUsers = ref(0)
const userVisible = ref(false)
const roleVisible = ref(false)
const detailVisible = ref(false)
const editingUserId = ref<number | null>(null)
const roleTargetUserId = ref<number | null>(null)
const roleTargetTenantId = ref<string>('')
const roleTargetIsCurrentUser = ref(false)
const detailData = ref<UserSummary | null>(null)
const selectedRoleCodes = ref<string[]>([])
const loading = ref(false)
const formRef = ref<FormInstance>()
const userFormTenantId = ref('')
const queryDeptKey = ref<string | undefined>()

const queryParams = reactive({
  tenantId: undefined as string | undefined,
  username: '',
  mobile: '',
  email: '',
  deptId: undefined as number | undefined,
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
  roleCodes: [] as string[],
})

const defaultPasswordPolicy: SecurityPasswordPolicy = {
  passwordMinLength: 8,
  passwordMaxLength: 64,
  passwordRequireLetter: true,
  passwordRequireNumber: true,
  passwordRequireSpecial: false,
}
const passwordPolicy = ref<SecurityPasswordPolicy>(defaultPasswordPolicy)

function passwordPolicyMessage(policy = passwordPolicy.value) {
  const requirements = [`长度需在 ${policy.passwordMinLength} 到 ${policy.passwordMaxLength} 位之间`]
  if (policy.passwordRequireLetter) {
    requirements.push('包含字母')
  }
  if (policy.passwordRequireNumber) {
    requirements.push('包含数字')
  }
  if (policy.passwordRequireSpecial) {
    requirements.push('包含特殊字符')
  }
  requirements.push('不能包含空白字符')
  return `密码${requirements.join('，')}`
}

function validatePasswordByPolicy(value: string, policy = passwordPolicy.value) {
  if (value.length < policy.passwordMinLength || value.length > policy.passwordMaxLength) {
    return false
  }
  if (/\s/.test(value)) {
    return false
  }
  if (policy.passwordRequireLetter && !/[A-Za-z]/.test(value)) {
    return false
  }
  if (policy.passwordRequireNumber && !/\d/.test(value)) {
    return false
  }
  if (policy.passwordRequireSpecial && !/[^A-Za-z\d\s]/.test(value)) {
    return false
  }
  return true
}

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
        if (value && !validatePasswordByPolicy(String(value))) {
          callback(new Error(passwordPolicyMessage()))
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
  roleCodes: [
    {
      validator: (_rule, value, callback) => {
        if (!Array.isArray(value) || value.length === 0) {
          callback(new Error('请至少选择一个角色'))
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
const isPlatformAdmin = computed(() => Boolean(authStore.snapshot?.superAdmin))
const tenantOptions = computed<TenantOption[]>(() => buildTenantOptions())
const queryDepartmentTree = computed(() => {
  const visibleDepartments = queryParams.tenantId
    ? departments.value.filter((item) => item.tenantId === queryParams.tenantId)
    : departments.value
  return buildDepartmentTree(visibleDepartments, isPlatformAdmin.value && !queryParams.tenantId)
})
const userDepartmentTree = computed(() => buildDepartmentTree(
  departments.value.filter((item) => item.tenantId === userFormTenantId.value),
))
const userAssignableRoles = computed(() => {
  if (!userFormTenantId.value) {
    return roles.value
  }
  return roles.value.filter((role) => role.tenantId === userFormTenantId.value)
})
const roleAssignmentRoles = computed(() => {
  if (!roleTargetTenantId.value) {
    return []
  }
  return roles.value.filter((role) => role.tenantId === roleTargetTenantId.value)
})
const roleAssignmentRoleCodeSet = computed(() => new Set(roleAssignmentRoles.value.map((role) => role.code)))
const userTablePrefs = useTablePreferences('eap.table.users', [
  { key: 'tenantId', label: '租户', width: 130 },
  { key: 'username', label: '用户名', width: 140 },
  { key: 'displayName', label: '显示名称', width: 140 },
  { key: 'mobile', label: '手机号', width: 140 },
  { key: 'email', label: '邮箱', width: 200 },
  { key: 'enabled', label: '状态', width: 100 },
  { key: 'roles', label: '角色', width: 200 },
  { key: 'actions', label: '操作', width: 320 },
])

void bootstrap()

async function bootstrap() {
  await Promise.all([loadPasswordPolicy(), load()])
}

async function loadPasswordPolicy() {
  try {
    passwordPolicy.value = await queryPasswordPolicy()
  } catch {
    passwordPolicy.value = defaultPasswordPolicy
  }
}

async function load() {
  loading.value = true
  try {
    const shouldLoadRoles = authStore.snapshot?.grants.includes('upms:sysrole:page')
    const [userPage, roleList, departmentList, tenantList] = await Promise.all([
      queryUsers(queryParams),
      shouldLoadRoles ? queryRoleOptions() : Promise.resolve([] as RoleView[]),
      queryDepartments(),
      loadTenantCatalog(),
    ])
    users.value = userPage.records
    totalUsers.value = userPage.total
    roles.value = roleList
    departments.value = departmentList
    tenantCatalog.value = tenantList
  } finally {
    loading.value = false
  }
}

async function loadTenantCatalog() {
  if (!isPlatformAdmin.value) {
    return [] as TenantView[]
  }
  try {
    const page = await queryTenants({ page: 1, size: 500 }, { suppressErrorMessage: true })
    return page.records
  } catch {
    return [] as TenantView[]
  }
}

async function refreshRolesIfAllowed() {
  const shouldLoadRoles = authStore.snapshot?.grants.includes('upms:sysrole:page')
  if (!shouldLoadRoles) {
    return
  }
  roles.value = await queryRoleOptions()
}

function mergeRoleOptions(roleList: RoleView[]) {
  if (roleList.length === 0) {
    return
  }
  const roleMap = new Map(roles.value.map((role) => [`${role.tenantId ?? ''}:${role.id}`, role]))
  roleList.forEach((role) => roleMap.set(`${role.tenantId ?? ''}:${role.id}`, role))
  roles.value = Array.from(roleMap.values())
}

function normalizeRoleCodes(roleCodes: string[]) {
  return Array.from(new Set(roleCodes.map((code) => code.trim()).filter(Boolean)))
}

function roleKey(role: RoleView) {
  return `${role.tenantId ?? ''}:${role.id}:${role.code}`
}

function buildTenantOptions() {
  const tenantMap = new Map<string, TenantOption>()
  const addTenant = (tenantId?: string | null, name?: string | null) => {
    if (!tenantId) {
      return
    }
    const label = name ? `${name} (${tenantId})` : tenantId
    tenantMap.set(tenantId, { tenantId, label })
  }
  tenantCatalog.value.forEach((tenant) => addTenant(tenant.tenantId, tenant.name))
  users.value.forEach((user) => addTenant(user.tenantId))
  departments.value.forEach((department) => addTenant(department.tenantId))
  roles.value.forEach((role) => addTenant(role.tenantId))
  addTenant(authStore.snapshot?.operatorTenantId || authStore.operatorTenantId || authStore.snapshot?.tenantId || authStore.tenantId)
  return Array.from(tenantMap.values()).sort((left, right) => {
    if (left.tenantId === 'platform') {
      return -1
    }
    if (right.tenantId === 'platform') {
      return 1
    }
    return left.tenantId.localeCompare(right.tenantId)
  })
}

function defaultTenantId() {
  return queryParams.tenantId
    || authStore.snapshot?.operatorTenantId
    || authStore.operatorTenantId
    || authStore.snapshot?.tenantId
    || authStore.tenantId
    || tenantOptions.value[0]?.tenantId
    || 'platform'
}

function defaultDeptIdForTenant(tenantId: string) {
  return departments.value.find((department) => department.tenantId === tenantId)?.id ?? null
}

function parseDepartmentKey(value?: string | number | null) {
  if (value == null || value === '') {
    return null
  }
  const [tenantId, rawId] = String(value).split(':')
  const id = Number(rawId)
  if (!tenantId || !Number.isFinite(id)) {
    return null
  }
  return { tenantId, id }
}

function handleQueryTenantChange() {
  queryDeptKey.value = undefined
  queryParams.deptId = undefined
}

function handleQueryDeptChange(value?: string | number) {
  const parsed = parseDepartmentKey(value)
  queryParams.deptId = parsed?.id
  if (parsed?.tenantId) {
    queryParams.tenantId = parsed.tenantId
  }
}

function handleUserTenantChange() {
  userForm.deptId = defaultDeptIdForTenant(userFormTenantId.value)
  userForm.roleCodes = []
}

function doSearch() {
  queryParams.page = 1
  void load()
}

function resetSearch() {
  queryParams.tenantId = undefined
  queryParams.username = ''
  queryParams.mobile = ''
  queryParams.email = ''
  queryParams.deptId = undefined
  queryDeptKey.value = undefined
  queryParams.enabled = undefined
  queryParams.page = 1
  void load()
}

function openDetail(row: UserSummary) {
  detailData.value = row
  detailVisible.value = true
}

function isCurrentUser(row: UserSummary) {
  return authStore.snapshot?.userId === row.id
}

function openUser(row?: UserSummary) {
  editingUserId.value = row?.id ?? null
  userFormTenantId.value = row?.tenantId || defaultTenantId()
  Object.assign(userForm, {
    username: row?.username ?? '',
    displayName: row?.displayName ?? '',
    mobile: row?.mobile ?? '',
    email: row?.email ?? '',
    password: '',
    deptId: row?.deptId ?? defaultDeptIdForTenant(userFormTenantId.value),
    enabled: row?.enabled ?? true,
    roleCodes: [...(row?.roles ?? [])],
  })
  userVisible.value = true
}

async function submitUser() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()

  if (editingUserId.value && editingUserId.value === authStore.snapshot?.userId && !userForm.enabled) {
    ElMessage.warning('不能停用当前登录用户')
    return
  }
  const payload = {
    tenantId: userFormTenantId.value,
    username: userForm.username,
    displayName: userForm.displayName || null,
    mobile: userForm.mobile || null,
    email: userForm.email || null,
    password: userForm.password || undefined,
    deptId: userForm.deptId,
    enabled: userForm.enabled,
    roleCodes: userForm.roleCodes,
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
  roleTargetTenantId.value = row.tenantId || ''
  roleTargetIsCurrentUser.value = isCurrentUser(row)
  await refreshRolesIfAllowed()
  const assignedRoles = await queryAssignedRoles(row.id)
  mergeRoleOptions(assignedRoles)
  selectedRoleCodes.value = normalizeRoleCodes(assignedRoles.map((item) => item.code))
    .filter((code) => roleAssignmentRoleCodeSet.value.has(code))
  roleVisible.value = true
}

async function submitRoleAssignment() {
  if (!roleTargetUserId.value) {
    return
  }
  const normalizedRoleCodes = normalizeRoleCodes(selectedRoleCodes.value)
    .filter((code) => roleAssignmentRoleCodeSet.value.has(code))
  if (normalizedRoleCodes.length === 0) {
    ElMessage.warning(roleTargetIsCurrentUser.value ? '不能移除当前登录用户的全部角色' : '请至少选择一个角色')
    return
  }
  try {
    await assignUserRoles(roleTargetUserId.value, normalizedRoleCodes)
    roleVisible.value = false
    ElMessage.success('用户角色已更新')
    await load()
  } catch {
    // 错误信息由统一的 HTTP 拦截器处理。
  }
}

function promptResetPassword(row: UserSummary) {
  ElMessageBox.prompt('请输入新密码', '重置密码', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputType: 'password',
    inputValidator: (value) => validatePasswordByPolicy(value) || passwordPolicyMessage(),
  })
    .then(async ({ value }) => {
      await updateUser(row.id, {
        tenantId: row.tenantId,
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

async function removeUser(row: UserSummary) {
  if (isCurrentUser(row)) {
    ElMessage.warning('不能删除当前登录用户')
    return
  }
  await ElMessageBox.confirm('删除用户后将无法继续登录，是否继续？', '删除确认', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('用户已删除')
  await load()
}

function buildDepartmentTree(source: DepartmentView[], showTenant = false) {
  const nodes = source.map((item) => ({
    id: item.id,
    key: departmentKey(item),
    label: showTenant && item.tenantId ? `${item.tenantId} / ${item.name}` : item.name,
    tenantId: item.tenantId || '',
    parentId: item.parentId ?? 0,
    children: [] as DepartmentTreeNode[],
  }))
  const map = new Map(nodes.map((item) => [item.key, item]))
  const roots: typeof nodes = []
  nodes.forEach((node) => {
    const parent = node.parentId ? map.get(`${node.tenantId}:${node.parentId}`) : null
    if (parent) {
      parent.children.push(node)
      return
    }
    roots.push(node)
  })
  pruneEmptyChildren(roots)
  return roots
}

function pruneEmptyChildren(nodes: DepartmentTreeNode[]) {
  nodes.forEach((node) => {
    if (!node.children || node.children.length === 0) {
      delete node.children
      return
    }
    pruneEmptyChildren(node.children)
  })
}

function departmentKey(department: Pick<DepartmentView, 'tenantId' | 'id'>) {
  return `${department.tenantId || ''}:${department.id}`
}

function onUserHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  userTablePrefs.setColumnWidth(key, newWidth)
}
</script>
