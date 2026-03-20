<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Roles</span>
        <strong>{{ filteredRoles.length }}</strong>
        <span>当前筛选条件下的角色总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">All Scope</span>
        <strong>{{ allScopeCount }}</strong>
        <span>全量数据范围角色</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Dept Scope</span>
        <strong>{{ deptScopeCount }}</strong>
        <span>部门级数据范围角色</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Custom Scope</span>
        <strong>{{ customScopeCount }}</strong>
        <span>自定义数据范围角色</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Roles</span>
          <h3>角色管理</h3>
        </div>
        <el-button type="primary" @click="openRole()">新增角色</el-button>
      </div>

      <el-form :inline="true" class="toolbar-inline" @submit.prevent="handleSearch">
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="搜索角色名称、编码或描述" clearable />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="scopeFilter" placeholder="全部" clearable style="width: 180px">
            <el-option label="全部" value="" />
            <el-option label="全部数据" value="ALL" />
            <el-option label="仅本人" value="SELF" />
            <el-option label="本部门" value="DEPT" />
            <el-option label="本部门及子部门" value="DEPT_AND_CHILDREN" />
            <el-option label="自定义范围" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="pagedRoles" stripe>
        <el-table-column prop="code" label="角色编码" min-width="140" />
        <el-table-column prop="name" label="角色名称" min-width="160" />
        <el-table-column prop="description" label="角色描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="dataScopeType" label="数据范围" min-width="140" />
        <el-table-column label="权限数" width="100">
          <template #default="{ row }">{{ permissionCountMap[row.id] ?? 0 }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="320">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openRole(row)">编辑</el-button>
            <el-button link type="primary" @click="openPermissionAssignment(row)">分配权限</el-button>
            <el-button link type="danger" @click="removeRole(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="filteredRoles.length"
          @size-change="handlePageChange"
          @current-change="handlePageChange"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="角色详情" size="680px">
      <template v-if="detailRole">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="角色编码">{{ detailRole.code }}</el-descriptions-item>
          <el-descriptions-item label="角色名称">{{ detailRole.name }}</el-descriptions-item>
          <el-descriptions-item label="数据范围">{{ detailRole.dataScopeType }}</el-descriptions-item>
          <el-descriptions-item label="已分配权限">{{ assignedPermissionCodes.length }}</el-descriptions-item>
          <el-descriptions-item label="角色描述" :span="2">{{ detailRole.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="tree-panel">
          <div class="tree-panel__head">
            <strong>权限树</strong>
            <span>{{ assignedPermissionCodes.length }} 个权限点</span>
          </div>
          <el-tree
            :data="detailPermissionTree"
            node-key="id"
            default-expand-all
            :props="{ children: 'children', label: 'label' }"
            empty-text="暂无权限"
          />
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="roleVisible" :title="editingRoleId ? '编辑角色' : '新增角色'" width="640px">
      <el-form ref="formRef" label-position="top" :model="roleForm" :rules="roleRules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="角色编码" prop="roleCode">
              <el-input v-model="roleForm.roleCode" :disabled="Boolean(editingRoleId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色名称" prop="roleName">
              <el-input v-model="roleForm.roleName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="角色描述">
          <el-input v-model="roleForm.roleDesc" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="数据范围" prop="dataScopeType">
          <el-select v-model="roleForm.dataScopeType" style="width: 100%">
            <el-option label="全部数据" value="ALL" />
            <el-option label="仅本人" value="SELF" />
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
      <div class="assignment-toolbar">
        <el-input v-model="permissionKeyword" placeholder="筛选权限名称、编码、资源或动作" clearable />
      </div>
      <el-tree
        ref="permissionTreeRef"
        :data="filteredPermissionTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ children: 'children', label: 'label' }"
        class="permission-tree"
      />
      <template #footer>
        <el-button @click="permissionVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPermissionAssignment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTree, FormInstance, FormRules } from 'element-plus'
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

type PermissionTreeNode = {
  id: string
  label: string
  children?: PermissionTreeNode[]
}

const roles = ref<RoleView[]>([])
const permissions = ref<PermissionView[]>([])
const permissionTreeRef = ref<InstanceType<typeof ElTree>>()
const formRef = ref<FormInstance>()
const roleVisible = ref(false)
const permissionVisible = ref(false)
const detailVisible = ref(false)
const editingRoleId = ref<number | null>(null)
const permissionTargetRoleId = ref<number | null>(null)
const detailRole = ref<RoleView | null>(null)
const assignedPermissions = ref<PermissionView[]>([])
const loading = ref(false)
const keyword = ref('')
const scopeFilter = ref('')
const permissionKeyword = ref('')
const page = ref(1)
const size = ref(10)

const roleForm = reactive({
  roleCode: '',
  roleName: '',
  roleDesc: '',
  dataScopeType: 'ALL',
})

const roleRules = reactive<FormRules>({
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]{2,64}$/, message: '角色编码仅支持字母、数字、:、_、-', trigger: 'blur' },
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  dataScopeType: [{ required: true, message: '请选择数据范围', trigger: 'change' }],
})

const permissionCountMap = computed<Record<number, number>>(() => {
  const map: Record<number, number> = {}
  for (const role of roles.value) {
    map[role.id] = 0
  }
  if (detailRole.value) {
    map[detailRole.value.id] = assignedPermissions.value.length
  }
  return map
})

function buildPermissionTree(source: PermissionView[]) {
  const resourceMap = new Map<string, PermissionTreeNode>()
  for (const permission of source) {
    const resourceLabel = `${permission.resourceCode}`
    const actionLabel = `${permission.actionCode} / ${permission.scopeCode}`
    let resourceNode = resourceMap.get(permission.resourceCode)
    if (!resourceNode) {
      resourceNode = {
        id: `RES_${permission.resourceCode}`,
        label: resourceLabel,
        children: [],
      }
      resourceMap.set(permission.resourceCode, resourceNode)
    }

    let actionNode = resourceNode.children?.find((item) => item.id === `ACT_${permission.resourceCode}_${permission.actionCode}_${permission.scopeCode}`)
    if (!actionNode) {
      actionNode = {
        id: `ACT_${permission.resourceCode}_${permission.actionCode}_${permission.scopeCode}`,
        label: actionLabel,
        children: [],
      }
      resourceNode.children?.push(actionNode)
    }

    actionNode.children?.push({
      id: permission.permissionCode,
      label: `${permission.permissionName || permission.permissionCode} (${permission.permissionCode})`,
    })
  }
  return Array.from(resourceMap.values())
}

const permissionTreeData = computed(() => buildPermissionTree(permissions.value))
const detailPermissionTree = computed(() => buildPermissionTree(assignedPermissions.value))

const filteredPermissionTree = computed(() => {
  const normalizedKeyword = permissionKeyword.value.trim().toLowerCase()
  if (!normalizedKeyword) {
    return permissionTreeData.value
  }
  return filterTree(permissionTreeData.value, normalizedKeyword)
})

const filteredRoles = computed(() =>
  roles.value.filter((role) => {
    const normalizedKeyword = keyword.value.trim().toLowerCase()
    const matchesKeyword =
      !normalizedKeyword ||
      [role.code, role.name, role.description || ''].some((value) => value.toLowerCase().includes(normalizedKeyword))
    const matchesScope = !scopeFilter.value || role.dataScopeType === scopeFilter.value
    return matchesKeyword && matchesScope
  }),
)

const pagedRoles = computed(() => {
  const start = (page.value - 1) * size.value
  return filteredRoles.value.slice(start, start + size.value)
})

const assignedPermissionCodes = computed(() => assignedPermissions.value.map((item) => item.permissionCode))
const allScopeCount = computed(() => filteredRoles.value.filter((item) => item.dataScopeType === 'ALL').length)
const deptScopeCount = computed(() =>
  filteredRoles.value.filter((item) => item.dataScopeType === 'DEPT_AND_CHILDREN' || item.dataScopeType === 'DEPT').length,
)
const customScopeCount = computed(() => filteredRoles.value.filter((item) => item.dataScopeType === 'CUSTOM').length)

void load()

async function load() {
  loading.value = true
  try {
    const [roleList, permissionList] = await Promise.all([queryRoles(), queryPermissions()])
    roles.value = roleList
    permissions.value = permissionList
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
}

function resetSearch() {
  keyword.value = ''
  scopeFilter.value = ''
  page.value = 1
}

function handlePageChange() {
  return
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

async function openDetail(row: RoleView) {
  detailRole.value = row
  assignedPermissions.value = await queryAssignedPermissions(row.id)
  detailVisible.value = true
}

async function submitRole() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
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
  permissionVisible.value = true
  permissionKeyword.value = ''
  await nextTick()
  permissionTreeRef.value?.setCheckedKeys(assigned.map((item) => item.permissionCode))
}

async function submitPermissionAssignment() {
  if (!permissionTargetRoleId.value) {
    return
  }
  const selectedCodes = (permissionTreeRef.value?.getCheckedKeys(true) || []) as string[]
  await assignRolePermissions(permissionTargetRoleId.value, selectedCodes)
  permissionVisible.value = false
  ElMessage.success('角色权限已更新')
  if (detailRole.value?.id === permissionTargetRoleId.value) {
    assignedPermissions.value = await queryAssignedPermissions(permissionTargetRoleId.value)
  }
  await load()
}

async function removeRole(id: number) {
  await ElMessageBox.confirm('删除角色后，原有关联授权将失效，是否继续？', '删除确认', { type: 'warning' })
  await deleteRole(id)
  ElMessage.success('角色已删除')
  await load()
}

function filterTree(nodes: PermissionTreeNode[], keywordValue: string): PermissionTreeNode[] {
  return nodes
    .map((node) => {
      const children = node.children ? filterTree(node.children, keywordValue) : []
      const matched = node.label.toLowerCase().includes(keywordValue)
      if (matched || children.length > 0) {
        return {
          ...node,
          children: children.length > 0 ? children : node.children,
        }
      }
      return null
    })
    .filter((item): item is PermissionTreeNode => Boolean(item))
}
</script>

<style scoped lang="scss">
.assignment-toolbar {
  margin-bottom: 16px;
}

.permission-tree {
  max-height: 480px;
  overflow-y: auto;
}

.tree-panel {
  margin-top: 20px;
  padding: 16px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.9);
}

.tree-panel__head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #475569;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
