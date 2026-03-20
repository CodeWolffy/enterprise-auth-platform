<template>
  <div class="panel-stack">
    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Departments</span>
          <h3>部门管理</h3>
        </div>
        <el-button type="primary" @click="openDepartment()">新增部门</el-button>
      </div>

      <el-table :data="departments" stripe>
        <el-table-column prop="name" label="部门名称" min-width="180" />
        <el-table-column prop="code" label="部门编码" min-width="140" />
        <el-table-column prop="parentId" label="父部门 ID" min-width="100" />
        <el-table-column prop="leaderUserId" label="负责人用户 ID" min-width="120" />
        <el-table-column fixed="right" label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDepartment(row)">编辑</el-button>
            <el-button link type="danger" @click="removeDepartment(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑部门' : '新增部门'" width="620px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="部门名称"><el-input v-model="form.deptName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="部门编码"><el-input v-model="form.deptCode" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="父部门 ID"><el-input-number v-model="form.parentId" :min="1" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="负责人用户 ID"><el-input-number v-model="form.leaderUserId" :min="1" style="width: 100%" /></el-form-item></el-col>
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
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createDepartment, deleteDepartment, queryDepartments, updateDepartment } from '@/api/platform'
import type { DepartmentView } from '@/types/auth'

const departments = ref<DepartmentView[]>([])
const visible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  parentId: null as number | null,
  deptCode: '',
  deptName: '',
  leaderUserId: null as number | null,
})

void load()

async function load() {
  departments.value = await queryDepartments()
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
  await ElMessageBox.confirm('删除部门后相关组织结构将失效，是否继续？', '删除确认', { type: 'warning' })
  await deleteDepartment(id)
  ElMessage.success('部门已删除')
  await load()
}
</script>
