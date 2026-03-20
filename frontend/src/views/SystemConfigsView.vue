<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Configs</span>
        <strong>{{ total }}</strong>
        <span>当前筛选条件下的参数项总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Operators</span>
        <strong>{{ operatorCount }}</strong>
        <span>当前页涉及的创建人数</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Config</span>
          <h3>参数管理</h3>
        </div>
        <el-button type="primary" @click="openConfig()">新增参数</el-button>
      </div>

      <el-form :inline="true" class="toolbar-inline" @submit.prevent="handleSearch">
        <el-form-item label="参数分类">
          <el-select v-model="category" placeholder="全部分类" clearable style="width: 180px">
            <el-option v-for="item in categoryOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="搜索参数键、名称或值" clearable />
        </el-form-item>
        <el-form-item label="排序字段">
          <el-select v-model="sortBy" style="width: 160px">
            <el-option label="创建时间" value="createdAt" />
            <el-option label="参数键" value="configKey" />
            <el-option label="参数名称" value="configName" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序方向">
          <el-select v-model="sortDirection" style="width: 120px">
            <el-option label="升序" value="asc" />
            <el-option label="降序" value="desc" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="configs" stripe>
        <el-table-column prop="category" label="参数分类" min-width="120" />
        <el-table-column prop="configKey" label="参数键" min-width="180" />
        <el-table-column prop="configName" label="参数名称" min-width="180" />
        <el-table-column prop="configValue" label="参数值" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" min-width="120" />
        <el-table-column fixed="right" label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openConfig(row)">编辑</el-button>
            <el-button link type="danger" @click="removeConfig(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="参数详情" size="600px">
      <template v-if="detailItem">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="参数键">{{ detailItem.configKey }}</el-descriptions-item>
          <el-descriptions-item label="参数分类">{{ detailItem.category }}</el-descriptions-item>
          <el-descriptions-item label="参数名称">{{ detailItem.configName }}</el-descriptions-item>
          <el-descriptions-item label="参数值" :span="2">{{ detailItem.configValue }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailItem.createdBy }}</el-descriptions-item>
          <el-descriptions-item label="ID">{{ detailItem.id }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-dialog v-model="visible" :title="editingId ? '编辑参数' : '新增参数'" width="560px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <el-form-item label="参数键" prop="configKey">
          <el-input v-model="form.configKey" />
        </el-form-item>
        <el-form-item label="参数名称" prop="configName">
          <el-input v-model="form.configName" />
        </el-form-item>
        <el-form-item label="参数值" prop="configValue">
          <el-input v-model="form.configValue" type="textarea" :rows="4" />
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
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createConfig, deleteConfig, queryCategories, queryConfigs, updateConfig } from '@/api/system'
import type { CategoryOption, ConfigView } from '@/types/auth'

const configs = ref<ConfigView[]>([])
const visible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const detailItem = ref<ConfigView | null>(null)
const category = ref('')
const keyword = ref('')
const sortBy = ref<'createdAt' | 'configKey' | 'configName'>('createdAt')
const sortDirection = ref<'asc' | 'desc'>('asc')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const formRef = ref<FormInstance>()
const categoryOptions = ref<CategoryOption[]>([])

const form = reactive({
  configKey: '',
  configName: '',
  configValue: '',
})

const rules = reactive<FormRules>({
  configKey: [{ required: true, message: '请输入参数键', trigger: 'blur' }],
  configName: [{ required: true, message: '请输入参数名称', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入参数值', trigger: 'blur' }],
})

const operatorCount = computed(() => new Set(configs.value.map((item) => item.createdBy)).size)

void load()
void loadCategories()

async function load() {
  const result = await queryConfigs({
    keyword: keyword.value || undefined,
    category: category.value || undefined,
    page: page.value,
    size: size.value,
    sortBy: sortBy.value,
    sortDirection: sortDirection.value,
  })
  configs.value = result.records
  total.value = result.total
}

async function loadCategories() {
  const result = await queryCategories()
  categoryOptions.value = result.config || []
}

function handleSearch() {
  page.value = 1
  void load()
}

function resetSearch() {
  keyword.value = ''
  category.value = ''
  sortBy.value = 'createdAt'
  sortDirection.value = 'asc'
  page.value = 1
  void load()
}

function handleSizeChange(value: number) {
  size.value = value
  void load()
}

function handleCurrentChange(value: number) {
  page.value = value
  void load()
}

function openDetail(row: ConfigView) {
  detailItem.value = row
  detailVisible.value = true
}

function openConfig(row?: ConfigView) {
  editingId.value = row?.id ?? null
  Object.assign(form, row ?? { configKey: '', configName: '', configValue: '' })
  visible.value = true
}

async function submit() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
  if (editingId.value) {
    await updateConfig(editingId.value, form)
    ElMessage.success('参数已更新')
  } else {
    await createConfig(form)
    ElMessage.success('参数已创建')
  }
  visible.value = false
  await load()
}

async function removeConfig(id: number) {
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除确认', { type: 'warning' })
  await deleteConfig(id)
  ElMessage.success('参数已删除')
  await load()
}
</script>

<style scoped lang="scss">
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
