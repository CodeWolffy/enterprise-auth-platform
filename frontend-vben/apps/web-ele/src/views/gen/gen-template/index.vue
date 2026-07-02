<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElCol,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';
import { Plus, Refresh, Search } from '@element-plus/icons-vue';

import {
  getTemplates,
  createTemplate,
  updateTemplate,
  deleteTemplate,
} from '#/api/gen/table';

interface TemplateRow {
  id?: number;
  name: string;
  language: string;
  templateCategory: string;
  pathPattern?: string;
  pathMatchRegex?: string;
  content: string;
  description?: string;
  builtin?: boolean;
}

const LANGUAGE_OPTIONS = [
  { value: 'java', label: 'Java' },
  { value: 'typescript', label: 'TypeScript' },
  { value: 'vue', label: 'Vue' },
] as const;

const CATEGORY_OPTIONS = [
  { value: 'backend', label: '后端' },
  { value: 'frontend', label: '前端' },
  { value: 'api', label: '前端 API' },
  { value: 'type', label: '前端类型' },
  { value: 'view', label: '页面视图' },
] as const;

const categoryLabel = (value?: string) =>
  CATEGORY_OPTIONS.find((c) => c.value === value)?.label || value || '-';

const languageLabel = (value?: string) =>
  LANGUAGE_OPTIONS.find((l) => l.value === value)?.label || value || '-';

const asTemplateRow = (row: unknown) => row as TemplateRow;

// 列表
const loading = ref(false);
const records = ref<TemplateRow[]>([]);
const total = ref(0);
const query = reactive({ keyword: '', templateCategory: '', page: 1, size: 10 });

async function load() {
  loading.value = true;
  try {
    const res: any = await getTemplates({
      keyword: query.keyword || undefined,
      templateCategory: query.templateCategory || undefined,
      page: query.page,
      size: query.size,
    });
    records.value = res?.records ?? [];
    total.value = res?.total ?? 0;
  } finally {
    loading.value = false;
  }
}

function applySearch() {
  query.page = 1;
  load();
}

function resetSearch() {
  query.keyword = '';
  query.templateCategory = '';
  query.page = 1;
  load();
}

function handlePageChange(page: number) {
  query.page = page;
  load();
}

onMounted(() => load());

// 新建 / 编辑弹窗
const dialogVisible = ref(false);
const saving = ref(false);
const editing = ref<TemplateRow | null>(null);
const formRef = ref<any>(null);

const form = reactive<TemplateRow>({
  name: '',
  language: 'java',
  templateCategory: 'backend',
  pathMatchRegex: '',
  content: '',
  description: '',
});

function openCreate() {
  form.name = '';
  form.language = 'java';
  form.templateCategory = 'backend';
  form.pathMatchRegex = '';
  form.content = '';
  form.description = '';
  editing.value = null;
  dialogVisible.value = true;
}

function openEdit(row: TemplateRow) {
  editing.value = row;
  form.name = row.name;
  form.language = row.language;
  form.templateCategory = row.templateCategory;
  form.pathMatchRegex = row.pathMatchRegex ?? row.pathPattern ?? '';
  form.content = row.content;
  form.description = row.description ?? '';
  dialogVisible.value = true;
}

async function handleSave() {
  if (!form.name || !form.pathMatchRegex || !form.content) {
    ElMessage.warning('名称、路径匹配、模板内容均为必填');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      name: form.name.trim(),
      language: form.language,
      templateCategory: form.templateCategory,
      pathMatchRegex: form.pathMatchRegex.trim(),
      content: form.content,
    };
    if (editing.value?.id) {
      await updateTemplate(editing.value.id, payload);
      ElMessage.success('模板已更新');
    } else {
      await createTemplate(payload);
      ElMessage.success('模板已创建');
    }
    dialogVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: TemplateRow) {
  if (!row.id) return;
  await ElMessageBox.confirm(`确认删除模板「${row.name}」？`, '删除确认', { type: 'warning' });
  await deleteTemplate(row.id);
  ElMessage.success('模板已删除');
  await load();
}
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 工具栏 -->
      <div class="hx-table-toolbar" style="margin-bottom: 16px">
        <div><h2>自定义模板</h2></div>
        <ElButton :icon="Refresh" :loading="loading" @click="load">刷新</ElButton>
      </div>

      <!-- 搜索栏 -->
      <div class="hx-table-toolbar" style="margin-bottom: 12px; flex-wrap: wrap; gap: 10px">
        <ElRow :gutter="10" style="width: 100%">
          <ElCol :span="5">
            <ElInput v-model="query.keyword" placeholder="关键字搜索" clearable @keyup.enter="applySearch" />
          </ElCol>
          <ElCol :span="4">
            <ElSelect v-model="query.templateCategory" placeholder="分类" clearable>
              <ElOption v-for="c in CATEGORY_OPTIONS" :key="c.value" :label="c.label" :value="c.value" />
            </ElSelect>
          </ElCol>
          <ElCol :span="6">
            <ElButton type="primary" :icon="Search" @click="applySearch">查询</ElButton>
            <ElButton style="margin-left: 8px" @click="resetSearch">重置</ElButton>
          </ElCol>
          <ElCol :span="9" style="text-align: right">
            <ElButton type="primary" :icon="Plus" @click="openCreate">新增模板</ElButton>
          </ElCol>
        </ElRow>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="name" label="模板名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="语言" width="100">
          <template #default="{ row }">{{ languageLabel(row.language) }}</template>
        </el-table-column>
        <el-table-column label="分类" width="100">
          <template #default="{ row }">{{ categoryLabel(row.templateCategory) }}</template>
        </el-table-column>
        <el-table-column prop="pathMatchRegex" label="路径匹配" min-width="200" show-overflow-tooltip />
        <el-table-column label="内置" width="70">
          <template #default="{ row }">
            <ElTag v-if="row.builtin" size="small" type="info">内置</ElTag>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="描述" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <ElButton size="small" link type="primary" @click="openEdit(asTemplateRow(row))">编辑</ElButton>
            <ElButton
              v-if="!row.builtin"
              size="small"
              link
              type="danger"
              @click="handleDelete(asTemplateRow(row))"
            >
              删除
            </ElButton>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div v-if="total > 0" style="margin-top: 16px; text-align: right">
        <ElPagination
          :current-page="query.page"
          :page-size="query.size"
          :total="total"
          background
          layout="total, prev, pager, next, jumper"
          @current-change="handlePageChange"
        />
      </div>

      <!-- 编辑弹窗 -->
      <ElDialog
        v-model="dialogVisible"
        :title="editing ? '编辑模板' : '新增模板'"
        width="620px"
        destroy-on-close
        @close="dialogVisible = false"
      >
        <ElForm ref="formRef" :model="form" label-width="90px">
          <ElFormItem label="模板名称" required>
            <ElInput v-model="form.name" placeholder="例如：entity.java.ftl" :disabled="!!editing?.builtin" />
          </ElFormItem>
          <ElFormItem label="语言" required>
            <ElSelect v-model="form.language" :disabled="!!editing?.builtin">
              <ElOption v-for="l in LANGUAGE_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="分类" required>
            <ElSelect v-model="form.templateCategory" :disabled="!!editing?.builtin">
              <ElOption v-for="c in CATEGORY_OPTIONS" :key="c.value" :label="c.label" :value="c.value" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="路径匹配" required>
            <ElInput v-model="form.pathMatchRegex" placeholder="正则表达式，例如 .*\/entity\.java$" :disabled="!!editing?.builtin" />
          </ElFormItem>
          <ElFormItem label="描述">
            <ElInput
              v-model="form.description"
              type="textarea"
              :rows="2"
              placeholder="模板说明（可选）"
            />
          </ElFormItem>
          <ElFormItem label="模板内容" required>
            <ElInput
              v-model="form.content"
              type="textarea"
              :rows="12"
              placeholder="粘贴模板内容"
              :disabled="!!editing?.builtin"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="dialogVisible = false">取消</ElButton>
          <ElButton type="primary" :loading="saving" @click="handleSave">保存</ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
