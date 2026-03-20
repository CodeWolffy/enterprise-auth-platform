<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Departments</span>
        <strong>{{ departments.length }}</strong>
        <span>当前可见部门总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Root</span>
        <strong>{{ rootDepartmentCount }}</strong>
        <span>顶级部门数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Leaders</span>
        <strong>{{ leaderBoundCount }}</strong>
        <span>已配置负责人的部门</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Leaf</span>
        <strong>{{ leafDepartmentCount }}</strong>
        <span>末级部门数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Departments</span>
          <h3>部门管理</h3>
        </div>
        <el-button type="primary" @click="openDepartment()">新增部门</el-button>
      </div>

      <el-form :inline="true" class="toolbar-inline" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="按部门名称或编码搜索" clearable />
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="filteredDepartmentTree" stripe row-key="id" default-expand-all>
        <el-table-column prop="name" label="部门名称" min-width="180" />
        <el-table-column prop="code" label="部门编码" min-width="140" />
        <el-table-column prop="leaderUserId" label="负责人用户 ID" min-width="140" />
        <el-table-column fixed="right" label="操作" width="240">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDepartment(row)">编辑</el-button>
            <el-button link type="primary" @click="openChildDepartment(row)">新增子部门</el-button>
            <el-button link type="danger" @click="removeDepartment(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" title="部门详情" size="600px">
      <template v-if="detailDepartment">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="部门名称">{{ detailDepartment.name }}</el-descriptions-item>
          <el-descriptions-item label="部门编码">{{ detailDepartment.code || '-' }}</el-descriptions-item>
          <el-descriptions-item label="部门 ID">{{ detailDepartment.id }}</el-descriptions-item>
          <el-descriptions-item label="父级部门 ID">{{ detailDepartment.parentId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人用户 ID">{{ detailDepartment.leaderUserId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="直属子部门数">{{ childCount(detailDepartment.id) }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-dialog v-model="visible" :title="editingId ? '编辑部门' : '新增部门'" width="620px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部门名称">
              <el-input v-model="form.deptName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门编码">
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
            <el-form-item label="负责人用户 ID">
              <el-input-number v-model="form.leaderUserId" :min="1" style="width: 100%" />
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
import { createDepartment, deleteDepartment, queryDepartments, updateDepartment } from '@/api/platform'
import type { DepartmentView } from '@/types/auth'

type DepartmentTreeNode = DepartmentView & { children?: DepartmentTreeNode[] }

const departments = ref<DepartmentView[]>([])
const visible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const detailDepartment = ref<DepartmentView | null>(null)
const loading = ref(false)
const keyword = ref('')

const form = reactive({
  parentId: null as number | null,
  deptCode: '',
  deptName: '',
  leaderUserId: null as number | null,
})

const departmentTree = computed<DepartmentTreeNode[]>(() => buildTree(departments.value))
const filteredDepartmentTree = computed<DepartmentTreeNode[]>(() => {
  if (!keyword.value.trim()) {
    return departmentTree.value
  }
  return filterTreeByKeyword(departmentTree.value, keyword.value.trim().toLowerCase())
})

const rootDepartmentCount = computed(() => departments.value.filter((item) => !item.parentId).length)
const leaderBoundCount = computed(() => departments.value.filter((item) => Boolean(item.leaderUserId)).length)
const leafDepartmentCount = computed(() => {
  const parentIds = new Set(departments.value.map((item) => item.parentId).filter(Boolean))
  return departments.value.filter((item) => !parentIds.has(item.id)).length
})

void load()

async function load() {
  loading.value = true
  try {
    departments.value = await queryDepartments()
  } finally {
    loading.value = false
  }
}

function buildTree(source: DepartmentView[]) {
  const map = new Map<number, DepartmentTreeNode>()
  const roots: DepartmentTreeNode[] = []

  source.forEach((department) => {
    map.set(department.id, { ...department, children: [] })
  })

  source.forEach((department) => {
    const current = map.get(department.id)!
    if (department.parentId && map.has(department.parentId)) {
      map.get(department.parentId)!.children!.push(current)
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
    } else {
      pruneEmptyChildren(node.children)
    }
  })
}

function filterTreeByKeyword(nodes: DepartmentTreeNode[], normalizedKeyword: string): DepartmentTreeNode[] {
  return nodes
    .map((node) => {
      const children = node.children ? filterTreeByKeyword(node.children, normalizedKeyword) : []
      const matched = [node.name, node.code || ''].some((value) => value.toLowerCase().includes(normalizedKeyword))
      if (matched || children.length > 0) {
        return { ...node, children: children.length > 0 ? children : undefined }
      }
      return null
    })
    .filter((item): item is DepartmentTreeNode => Boolean(item))
}

function openDepartment(row?: DepartmentView) {
  editingId.value = row?.id ?? null
  Object.assign(form, {
    parentId: row?.parentId ?? null,
    deptCode: row?.code ?? '',
    deptName: row?.name ?? '',
    leaderUserId: row?.leaderUserId ?? null,
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
  })
  visible.value = true
}

async function submit() {
  const payload = {
    parentId: form.parentId,
    deptCode: form.deptCode || null,
    deptName: form.deptName,
    leaderUserId: form.leaderUserId,
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
</script>
