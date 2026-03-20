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
            <el-button link type="primary" @click="openAnalysis(row)">分析</el-button>
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

    <el-drawer v-model="analysisVisible" title="分类引用分析" size="720px">
      <template v-if="analysis">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="分类编码">{{ analysis.code }}</el-descriptions-item>
          <el-descriptions-item label="分类名称">{{ analysis.name }}</el-descriptions-item>
          <el-descriptions-item label="目标类型">{{ analysis.targetType }}</el-descriptions-item>
          <el-descriptions-item label="引用数量">{{ analysis.referenceCount }}</el-descriptions-item>
          <el-descriptions-item label="匹配规则" :span="2">
            <el-tag v-for="matcher in analysis.matchers" :key="matcher" class="scope-tag" size="small">
              {{ matcher }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <section class="analysis-section">
          <h4>引用样例</h4>
          <el-empty v-if="!analysis.sampleReferences.length" description="暂无引用样例" />
          <el-timeline v-else>
            <el-timeline-item v-for="item in analysis.sampleReferences" :key="item">
              {{ item }}
            </el-timeline-item>
          </el-timeline>
        </section>

        <section class="analysis-section">
          <h4>最近审计记录</h4>
          <el-empty v-if="!analysis.recentAudits.length" description="暂无分类变更审计" />
          <el-table v-else :data="analysis.recentAudits" stripe>
            <el-table-column prop="eventType" label="事件类型" min-width="180" />
            <el-table-column prop="operator" label="操作人" min-width="120" />
            <el-table-column prop="occurredAt" label="发生时间" min-width="180" />
            <el-table-column label="审计负载" min-width="260">
              <template #default="{ row }">
                <pre class="payload-pre">{{ row.payloadJson }}</pre>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createCategoryOption, deleteCategoryOption, queryCategoryAnalysis, queryCategoryOptions, updateCategoryOption } from '@/api/system'
import type { CategoryAnalysis, CategoryOption } from '@/types/auth'

const activeTab = ref<'dict' | 'config'>('dict')
const loading = ref(false)
const rows = ref<CategoryOption[]>([])
const visible = ref(false)
const analysisVisible = ref(false)
const editingCode = ref<string | null>(null)
const formRef = ref<FormInstance>()
const analysis = ref<CategoryAnalysis | null>(null)

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

async function openAnalysis(row: CategoryOption) {
  analysis.value = await queryCategoryAnalysis(activeTab.value, row.code)
  analysisVisible.value = true
}
</script>

<style scoped lang="scss">
.scope-tag {
  margin-right: 6px;
  margin-bottom: 6px;
}

.analysis-section {
  margin-top: 20px;
}

.payload-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
