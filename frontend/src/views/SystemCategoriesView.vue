<template>
  <div class="panel-stack">
    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Categories</span>
          <h3>分类配置管理</h3>
        </div>
        <el-button type="primary" @click="openDialog()">新增分类</el-button>
      </div>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="字典分类" name="dict" />
        <el-tab-pane label="参数分类" name="config" />
      </el-tabs>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="code" label="分类编码" min-width="160" />
        <el-table-column prop="name" label="分类名称" min-width="180" />
        <el-table-column label="匹配规则" min-width="320">
          <template #default="{ row }">
            <el-tag v-for="matcher in row.matchers" :key="matcher" class="scope-tag" size="small">
              {{ matcher }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="removeRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingCode ? '编辑分类' : '新增分类'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="分类编码" prop="code">
          <el-input v-model="form.code" :disabled="Boolean(editingCode)" />
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="匹配规则" prop="matchersText">
          <el-input
            v-model="form.matchersText"
            type="textarea"
            :rows="4"
            placeholder="每行一个匹配规则，例如 oauth.*"
          />
        </el-form-item>
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
import type { FormInstance, FormRules } from 'element-plus'
import { createCategoryOption, deleteCategoryOption, queryCategoryOptions, updateCategoryOption } from '@/api/system'
import type { CategoryOption } from '@/types/auth'

const activeTab = ref<'dict' | 'config'>('dict')
const loading = ref(false)
const rows = ref<CategoryOption[]>([])
const visible = ref(false)
const editingCode = ref<string | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  code: '',
  name: '',
  matchersText: '',
})

const rules = reactive<FormRules>({
  code: [
    { required: true, message: '请输入分类编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]{2,64}$/, message: '分类编码仅支持字母、数字、:、_、-', trigger: 'blur' },
  ],
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  matchersText: [{ required: true, message: '请至少填写一个匹配规则', trigger: 'blur' }],
})

void load()

async function load() {
  loading.value = true
  try {
    rows.value = await queryCategoryOptions(activeTab.value)
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  void load()
}

function openDialog(row?: CategoryOption) {
  editingCode.value = row?.code ?? null
  form.code = row?.code ?? ''
  form.name = row?.name ?? ''
  form.matchersText = row?.matchers?.join('\n') ?? ''
  visible.value = true
}

function normalizeMatchers() {
  return form.matchersText
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
}

async function submit() {
  await formRef.value?.validate()
  const payload = {
    code: form.code,
    name: form.name,
    matchers: normalizeMatchers(),
  }
  if (editingCode.value) {
    await updateCategoryOption(activeTab.value, editingCode.value, payload)
    ElMessage.success('分类已更新')
  } else {
    await createCategoryOption(activeTab.value, payload)
    ElMessage.success('分类已创建')
  }
  visible.value = false
  await load()
}

async function removeRow(row: CategoryOption) {
  await ElMessageBox.confirm(`确定删除分类 ${row.name} 吗？`, '删除确认', { type: 'warning' })
  await deleteCategoryOption(activeTab.value, row.code)
  ElMessage.success('分类已删除')
  await load()
}
</script>

<style scoped lang="scss">
.scope-tag {
  margin-right: 6px;
  margin-bottom: 6px;
}
</style>
