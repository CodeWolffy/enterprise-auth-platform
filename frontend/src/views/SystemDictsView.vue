<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Dict Items</span>
        <strong>{{ filteredDicts.length }}</strong>
        <span>当前筛选条件下的字典项总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Types</span>
        <strong>{{ dictTypeCount }}</strong>
        <span>字典类型种类数</span>
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

      <el-form :inline="true" class="toolbar-inline" @submit.prevent>
        <el-form-item label="字典类型">
          <el-input v-model="typeKeyword" placeholder="输入字典类型" clearable />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="搜索字典编码或字典值" clearable />
        </el-form-item>
      </el-form>

      <el-table :data="filteredDicts" stripe>
        <el-table-column prop="dictType" label="字典类型" min-width="140" />
        <el-table-column prop="dictCode" label="字典编码" min-width="160" />
        <el-table-column prop="dictValue" label="字典值" min-width="180" />
        <el-table-column prop="createdBy" label="创建人" min-width="120" />
        <el-table-column fixed="right" label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDict(row)">编辑</el-button>
            <el-button link type="danger" @click="removeDict(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑字典项' : '新增字典项'" width="520px">
      <el-form label-position="top">
        <el-form-item label="字典类型">
          <el-input v-model="form.dictType" />
        </el-form-item>
        <el-form-item label="字典编码">
          <el-input v-model="form.dictCode" />
        </el-form-item>
        <el-form-item label="字典值">
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
import { createDict, deleteDict, queryDicts, updateDict } from '@/api/system'
import type { DictView } from '@/types/auth'

const dicts = ref<DictView[]>([])
const visible = ref(false)
const editingId = ref<number | null>(null)
const keyword = ref('')
const typeKeyword = ref('')

const form = reactive({
  dictType: '',
  dictCode: '',
  dictValue: '',
})

const filteredDicts = computed(() =>
  dicts.value.filter((item) => {
    const normalizedType = typeKeyword.value.trim().toLowerCase()
    const normalizedKeyword = keyword.value.trim().toLowerCase()
    const matchesType = !normalizedType || item.dictType.toLowerCase().includes(normalizedType)
    const matchesKeyword =
      !normalizedKeyword ||
      [item.dictCode, item.dictValue].some((value) => value.toLowerCase().includes(normalizedKeyword))
    return matchesType && matchesKeyword
  }),
)

const dictTypeCount = computed(() => new Set(filteredDicts.value.map((item) => item.dictType)).size)

void load()

async function load() {
  dicts.value = await queryDicts()
}

function openDict(row?: DictView) {
  editingId.value = row?.id ?? null
  Object.assign(form, row ?? { dictType: '', dictCode: '', dictValue: '' })
  visible.value = true
}

async function submit() {
  if (editingId.value) {
    await updateDict(editingId.value, form)
    ElMessage.success('字典项已更新')
  } else {
    await createDict(form)
    ElMessage.success('字典项已创建')
  }
  visible.value = false
  await load()
}

async function removeDict(id: number) {
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除确认', { type: 'warning' })
  await deleteDict(id)
  ElMessage.success('字典项已删除')
  await load()
}
</script>
