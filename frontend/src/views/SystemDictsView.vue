<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Dict Items</span>
        <strong>{{ total }}</strong>
        <span>当前筛选条件下的字典项总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Types</span>
        <strong>{{ dictTypeCount }}</strong>
        <span>当前页覆盖的字典类型数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Dictionary</span>
          <h3>字典管理</h3>
        </div>
        <el-button type="primary" @click="openDict()">新增字典项</el-button>
      </div>

      <el-form :inline="true" class="toolbar-inline" @submit.prevent="handleSearch">
        <el-form-item label="字典类型">
          <el-input v-model="typeKeyword" placeholder="输入字典类型" clearable />
        </el-form-item>
        <el-form-item label="字典分类">
          <el-select v-model="category" placeholder="全部分类" clearable style="width: 180px">
            <el-option v-for="item in categoryOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="搜索字典编码或字典值" clearable />
        </el-form-item>
        <el-form-item label="排序字段">
          <el-select v-model="sortBy" style="width: 160px">
            <el-option label="创建时间" value="createdAt" />
            <el-option label="字典类型" value="dictType" />
            <el-option label="字典编码" value="dictCode" />
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

      <el-table :data="dicts" stripe>
        <el-table-column prop="category" label="字典分类" min-width="120" />
        <el-table-column prop="dictType" label="字典类型" min-width="140" />
        <el-table-column prop="dictCode" label="字典编码" min-width="160" />
        <el-table-column prop="dictValue" label="字典值" min-width="180" />
        <el-table-column prop="createdBy" label="创建人" min-width="120" />
        <el-table-column fixed="right" label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDict(row)">编辑</el-button>
            <el-button link type="danger" @click="removeDict(row.id)">删除</el-button>
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

    <el-drawer v-model="detailVisible" title="字典项详情" size="560px">
      <template v-if="detailItem">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="字典类型">{{ detailItem.dictType }}</el-descriptions-item>
          <el-descriptions-item label="字典分类">{{ detailItem.category }}</el-descriptions-item>
          <el-descriptions-item label="字典编码">{{ detailItem.dictCode }}</el-descriptions-item>
          <el-descriptions-item label="字典值" :span="2">{{ detailItem.dictValue }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailItem.createdBy }}</el-descriptions-item>
          <el-descriptions-item label="ID">{{ detailItem.id }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-dialog v-model="visible" :title="editingId ? '编辑字典项' : '新增字典项'" width="520px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="form.dictType" />
        </el-form-item>
        <el-form-item label="字典编码" prop="dictCode">
          <el-input v-model="form.dictCode" />
        </el-form-item>
        <el-form-item label="字典值" prop="dictValue">
          <el-input v-model="form.dictValue" />
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
import { createDict, deleteDict, queryCategories, queryDicts, updateDict } from '@/api/system'
import type { CategoryOption, DictView } from '@/types/auth'

const dicts = ref<DictView[]>([])
const visible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const detailItem = ref<DictView | null>(null)
const keyword = ref('')
const typeKeyword = ref('')
const category = ref('')
const sortBy = ref<'createdAt' | 'dictType' | 'dictCode'>('createdAt')
const sortDirection = ref<'asc' | 'desc'>('asc')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const formRef = ref<FormInstance>()
const categoryOptions = ref<CategoryOption[]>([])

const form = reactive({
  dictType: '',
  dictCode: '',
  dictValue: '',
})

function toDictPayload() {
  return {
    dictType: form.dictType.trim(),
    dictCode: form.dictCode.trim(),
    dictValue: form.dictValue.trim(),
  }
}

const rules = reactive<FormRules>({
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }],
  dictCode: [{ required: true, message: '请输入字典编码', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }],
})

const dictTypeCount = computed(() => new Set(dicts.value.map((item) => item.dictType)).size)

void load()
void loadCategories()

async function load() {
  const result = await queryDicts({
    dictType: typeKeyword.value || undefined,
    category: category.value || undefined,
    keyword: keyword.value || undefined,
    page: page.value,
    size: size.value,
    sortBy: sortBy.value,
    sortDirection: sortDirection.value,
  })
  dicts.value = result.records
  total.value = result.total
}

async function loadCategories() {
  const result = await queryCategories()
  categoryOptions.value = result.dict || []
}

function handleSearch() {
  page.value = 1
  void load()
}

function resetSearch() {
  keyword.value = ''
  typeKeyword.value = ''
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

function openDetail(row: DictView) {
  detailItem.value = row
  detailVisible.value = true
}

function openDict(row?: DictView) {
  editingId.value = row?.id ?? null
  Object.assign(form, {
    dictType: row?.dictType ?? '',
    dictCode: row?.dictCode ?? '',
    dictValue: row?.dictValue ?? '',
  })
  visible.value = true
}

async function submit() {
  if (!formRef.value) {
    return
  }
  try {
    await formRef.value.validate()
    const payload = toDictPayload()
    if (editingId.value !== null) {
      await updateDict(editingId.value, payload)
      ElMessage.success('字典项已更新')
    } else {
      await createDict(payload)
      ElMessage.success('字典项已创建')
    }
    visible.value = false
    await load()
  } catch {
    // Error toast is handled by HTTP interceptor; keep submit from bubbling unhandled rejections.
  }
}

async function removeDict(id: number) {
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除确认', { type: 'warning' })
  await deleteDict(id)
  ElMessage.success('字典项已删除')
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
