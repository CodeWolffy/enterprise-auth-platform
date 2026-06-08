<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">部门</span>
        <strong>{{ departments.length }}</strong>
        <span>当前可见部门总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">顶级</span>
        <strong>{{ rootDepartmentCount }}</strong>
        <span>顶级部门数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">负责人</span>
        <strong>{{ leaderBoundCount }}</strong>
        <span>已配置负责人部门数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">启用</span>
        <strong>{{ enabledDepartmentCount }}</strong>
        <span>当前启用部门数</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
<span class="eyebrow">部门</span>
          <h3>部门管理</h3>
        </div>
        <div class="panel-actions">
          <el-button @click="expandAll = !expandAll">{{ expandAll ? '收起全部' : '展开全部' }}</el-button>
          <el-button v-permission="'dept:write'" type="primary" @click="openDepartment()">新增部门</el-button>
        </div>
      </div>

      <AdvancedSearch @search="handleSearch" @reset="resetSearch">
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="按部门名称或编码搜索" clearable />
        </el-form-item>
      </AdvancedSearch>

      <div class="table-tools">
        <el-radio-group v-model="departmentTablePrefs.density" size="small">
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
              v-for="item in departmentTablePrefs.columns"
              :key="item.key"
              :model-value="departmentTablePrefs.visibleColumnMap[item.key]"
              @change="(value: boolean) => departmentTablePrefs.setColumnVisible(item.key, value)"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </el-popover>
        <el-button size="small" @click="departmentTablePrefs.reset()">恢复默认</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="filteredDepartmentTree"
        stripe
        row-key="id"
        :default-expand-all="expandAll"
        class="tree-table"
        :class="`table-density-${departmentTablePrefs.density}`"
        @header-dragend="onDepartmentHeaderDragEnd"
      >
        <el-table-column
          v-if="departmentTablePrefs.visibleColumnMap.name"
          column-key="name"
          prop="name"
          label="部门名称"
          min-width="220"
          :width="departmentTablePrefs.getColumnWidth('name')"
        />
        <el-table-column
          v-if="departmentTablePrefs.visibleColumnMap.code"
          column-key="code"
          prop="code"
          label="部门编码"
          min-width="160"
          :width="departmentTablePrefs.getColumnWidth('code')"
        />
        <el-table-column
          v-if="departmentTablePrefs.visibleColumnMap.leaderName"
          column-key="leaderName"
          label="负责人"
          min-width="150"
          :width="departmentTablePrefs.getColumnWidth('leaderName')"
        >
          <template #default="{ row }">{{ row.leaderName || row.leaderUserId || '-' }}</template>
        </el-table-column>
        <el-table-column
          v-if="departmentTablePrefs.visibleColumnMap.leaderPhone"
          column-key="leaderPhone"
          prop="leaderPhone"
          label="负责人电话"
          min-width="150"
          :width="departmentTablePrefs.getColumnWidth('leaderPhone')"
        >
          <template #default="{ row }">{{ row.leaderPhone || '-' }}</template>
        </el-table-column>
        <el-table-column
          v-if="departmentTablePrefs.visibleColumnMap.orderNo"
          column-key="orderNo"
          prop="orderNo"
          label="排序"
          min-width="100"
          :width="departmentTablePrefs.getColumnWidth('orderNo')"
        >
          <template #default="{ row }">{{ row.orderNo ?? 0 }}</template>
        </el-table-column>
        <el-table-column
          v-if="departmentTablePrefs.visibleColumnMap.enabled"
          column-key="enabled"
          label="状态"
          min-width="100"
          :width="departmentTablePrefs.getColumnWidth('enabled')"
        >
          <template #default="{ row }">
            <el-tag :type="isDepartmentEnabled(row) ? 'success' : 'info'" effect="light">
              {{ isDepartmentEnabled(row) ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="departmentTablePrefs.visibleColumnMap.childCount"
          column-key="childCount"
          label="直属子部门"
          min-width="130"
          :width="departmentTablePrefs.getColumnWidth('childCount')"
        >
          <template #default="{ row }">{{ childCount(row.id) }}</template>
        </el-table-column>
        <el-table-column
          v-if="departmentTablePrefs.visibleColumnMap.actions"
          column-key="actions"
          fixed="right"
          label="操作"
          :width="departmentTablePrefs.getColumnWidth('actions') || 300"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-permission="'dept:write'" link type="primary" @click="openDepartment(row)">编辑</el-button>
            <el-button v-permission="'dept:write'" link type="primary" @click="openChildDepartment(row)">新增子部门</el-button>
            <el-button v-permission="'dept:write'" link type="danger" @click="removeDepartment(row.id)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无部门数据" />
        </template>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" title="部门详情" size="620px">
      <template v-if="detailDepartment">
        <el-descriptions :column="2" border class="drawer-section drawer-section--overview">
          <el-descriptions-item label="部门名称">{{ detailDepartment.name }}</el-descriptions-item>
          <el-descriptions-item label="部门编码">{{ detailDepartment.code || '-' }}</el-descriptions-item>
          <el-descriptions-item label="部门 ID">{{ detailDepartment.id }}</el-descriptions-item>
          <el-descriptions-item label="父级部门 ID">{{ detailDepartment.parentId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人用户 ID">{{ detailDepartment.leaderUserId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detailDepartment.leaderName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人电话">{{ detailDepartment.leaderPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="排序">{{ detailDepartment.orderNo ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ isDepartmentEnabled(detailDepartment) ? '启用' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="直属子部门">{{ childCount(detailDepartment.id) }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-tip drawer-section drawer-section--guide">
          <el-alert
            title="部门树已接入数据权限过滤，当前列表仅展示当前用户有权访问的组织范围。"
            type="info"
            :closable="false"
          />
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="visible" :title="editingId ? '编辑部门' : '新增部门'" width="640px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="deptRules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部门名称" prop="deptName">
              <el-input v-model="form.deptName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门编码" prop="deptCode">
              <el-input v-model="form.deptCode" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="父级部门">
              <el-tree-select
                v-model="form.parentId"
                :data="departmentTree"
                :props="{ label: 'name', value: 'id', children: 'children' }"
                check-strictly
                clearable
                placeholder="留空表示顶级部门"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人用户 ID" prop="leaderUserId">
              <el-input-number v-model="form.leaderUserId" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="负责人姓名" prop="leaderName">
              <el-input v-model="form.leaderName" maxlength="128" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人电话" prop="leaderPhone">
              <el-input v-model="form.leaderPhone" maxlength="32" clearable />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序" prop="orderNo">
              <el-input-number v-model="form.orderNo" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="enabled">
              <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-permission="'dept:write'" type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import type { FormInstance, FormRules } from 'element-plus'
import { createDepartment, deleteDepartment, queryDepartments, updateDepartment } from '@/api/modules'
import { useTablePreferences } from '@/composables/useTablePreferences'
import type { DepartmentView } from '@/types/dept'

type DepartmentTreeNode = DepartmentView & { children?: DepartmentTreeNode[] }

const departments = ref<DepartmentView[]>([])
const visible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const detailDepartment = ref<DepartmentView | null>(null)
const loading = ref(false)
const expandAll = ref(true)
const keyword = ref('')
const formRef = ref<FormInstance>()

const departmentTablePrefs = useTablePreferences('table:departments', [
  { key: 'name', label: '部门名称', width: 220 },
  { key: 'code', label: '部门编码', width: 160 },
  { key: 'leaderName', label: '负责人', width: 150 },
  { key: 'leaderPhone', label: '负责人电话', width: 150 },
  { key: 'orderNo', label: '排序', width: 100 },
  { key: 'enabled', label: '状态', width: 100 },
  { key: 'childCount', label: '直属子部门', width: 130 },
  { key: 'actions', label: '操作', width: 300 },
])

const form = reactive({
  parentId: null as number | null,
  deptCode: '',
  deptName: '',
  leaderUserId: null as number | null,
  leaderName: '',
  leaderPhone: '',
  orderNo: 0,
  enabled: 1,
})

const deptRules = reactive<FormRules>({
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  deptCode: [
    { required: true, message: '请输入部门编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]{2,64}$/, message: '部门编码仅支持字母、数字、:、_、-', trigger: 'blur' },
  ],
  leaderUserId: [
    {
      validator: (_rule, value, callback) => {
        if (value == null) {
          callback()
          return
        }
        if (value < 1) {
          callback(new Error('负责人用户 ID 必须大于 0'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  leaderPhone: [{ pattern: /^[0-9+\-\s()]{0,32}$/, message: '负责人电话格式不正确', trigger: 'blur' }],
  orderNo: [{ type: 'number', min: 0, message: '排序不能小于 0', trigger: 'change' }],
})

const departmentTree = computed<DepartmentTreeNode[]>(() => buildTree(departments.value))
const filteredDepartmentTree = computed<DepartmentTreeNode[]>(() => {
  if (!keyword.value.trim()) {
    return departmentTree.value
  }
  return filterTreeByKeyword(departmentTree.value, keyword.value.trim().toLowerCase())
})

const rootDepartmentCount = computed(() => departments.value.filter((item) => !item.parentId).length)
const leaderBoundCount = computed(() => departments.value.filter((item) => Boolean(item.leaderName || item.leaderUserId)).length)
const enabledDepartmentCount = computed(() => departments.value.filter((item) => isDepartmentEnabled(item)).length)

void load()

async function load() {
  loading.value = true
  try {
    departments.value = await queryDepartments()
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  expandAll.value = true
}

function resetSearch() {
  keyword.value = ''
  expandAll.value = true
}

function buildTree(source: DepartmentView[]) {
  const map = new Map<number, DepartmentTreeNode>()
  const roots: DepartmentTreeNode[] = []

  source.forEach((department) => {
    map.set(department.id, { ...department, children: [] })
  })

  source.forEach((department) => {
    const current = map.get(department.id)
    if (!current) {
      return
    }
    if (department.parentId && map.has(department.parentId)) {
      map.get(department.parentId)?.children?.push(current)
    } else {
      roots.push(current)
    }
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

function filterTreeByKeyword(nodes: DepartmentTreeNode[], normalizedKeyword: string): DepartmentTreeNode[] {
  const filtered: DepartmentTreeNode[] = []
  nodes.forEach((node) => {
    const children = node.children ? filterTreeByKeyword(node.children, normalizedKeyword) : []
    const matched = [node.name, node.code || ''].some((value) => value.toLowerCase().includes(normalizedKeyword))
    if (matched || children.length > 0) {
      filtered.push({ ...node, children: children.length > 0 ? children : undefined })
    }
  })
  return filtered
}

function openDepartment(row?: DepartmentView) {
  editingId.value = row?.id ?? null
  Object.assign(form, {
    parentId: row?.parentId ?? null,
    deptCode: row?.code ?? '',
    deptName: row?.name ?? '',
    leaderUserId: row?.leaderUserId ?? null,
    leaderName: row?.leaderName ?? '',
    leaderPhone: row?.leaderPhone ?? '',
    orderNo: row?.orderNo ?? 0,
    enabled: row?.enabled ?? 1,
  })
  visible.value = true
}

function openDetail(row: DepartmentView) {
  detailDepartment.value = row
  detailVisible.value = true
}

function openChildDepartment(row: DepartmentView) {
  editingId.value = null
  Object.assign(form, {
    parentId: row.id,
    deptCode: '',
    deptName: '',
    leaderUserId: null,
    leaderName: '',
    leaderPhone: '',
    orderNo: 0,
    enabled: 1,
  })
  visible.value = true
}

async function submit() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
  const payload = {
    parentId: form.parentId,
    deptCode: form.deptCode || null,
    deptName: form.deptName,
    leaderUserId: form.leaderUserId,
    leaderName: form.leaderName || null,
    leaderPhone: form.leaderPhone || null,
    orderNo: form.orderNo ?? 0,
    enabled: form.enabled,
  }
  if (editingId.value) {
    await updateDepartment(editingId.value, payload)
    ElMessage.success('部门已更新')
  } else {
    await createDepartment(payload)
    ElMessage.success('部门已创建')
  }
  visible.value = false
  await load()
}

async function removeDepartment(id: number) {
  await ElMessageBox.confirm('删除部门后，相关组织结构将失效，是否继续？', '删除确认', { type: 'warning' })
  await deleteDepartment(id)
  ElMessage.success('部门已删除')
  await load()
}

function childCount(id: number) {
  return departments.value.filter((item) => item.parentId === id).length
}

function isDepartmentEnabled(department: DepartmentView) {
  return department.enabled !== 0
}

function onDepartmentHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  departmentTablePrefs.setColumnWidth(key, newWidth)
}
</script>

<style scoped lang="scss">
.tree-table :deep(.el-table__row .cell) {
  min-height: 24px;
}
</style>
