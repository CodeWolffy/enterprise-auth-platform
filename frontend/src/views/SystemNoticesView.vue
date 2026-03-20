<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Notices</span>
        <strong>{{ total }}</strong>
        <span>当前筛选条件下的公告总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Published</span>
        <strong>{{ publishedCount }}</strong>
        <span>当前页已发布公告数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Draft</span>
        <strong>{{ draftCount }}</strong>
        <span>当前页草稿数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Notice</span>
          <h3>公告管理</h3>
        </div>
        <el-button type="primary" @click="openNotice()">新增公告</el-button>
      </div>

      <el-form :inline="true" class="toolbar-inline" @submit.prevent="handleSearch">
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="搜索标题或内容" clearable />
        </el-form-item>
        <el-form-item label="发布状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="已发布" value="published" />
            <el-option label="草稿" value="draft" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序字段">
          <el-select v-model="sortBy" style="width: 160px">
            <el-option label="发布时间" value="publishTime" />
            <el-option label="创建时间" value="createdAt" />
            <el-option label="标题" value="noticeTitle" />
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

      <el-table :data="notices" stripe>
        <el-table-column prop="noticeTitle" label="标题" min-width="180" />
        <el-table-column prop="noticeContent" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="发布状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.published ? 'success' : 'info'">{{ row.published ? '已发布' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" min-width="180" />
        <el-table-column prop="createdBy" label="创建人" min-width="120" />
        <el-table-column fixed="right" label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openNotice(row)">编辑</el-button>
            <el-button link type="danger" @click="removeNotice(row.id)">删除</el-button>
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

    <el-drawer v-model="detailVisible" title="公告详情" size="620px">
      <template v-if="detailItem">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题" :span="2">{{ detailItem.noticeTitle }}</el-descriptions-item>
          <el-descriptions-item label="发布状态">
            {{ detailItem.published ? '已发布' : '草稿' }}
          </el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ detailItem.publishTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailItem.createdBy }}</el-descriptions-item>
          <el-descriptions-item label="ID">{{ detailItem.id }}</el-descriptions-item>
          <el-descriptions-item label="公告内容" :span="2">{{ detailItem.noticeContent }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <el-dialog v-model="visible" :title="editingId ? '编辑公告' : '新增公告'" width="680px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <el-form-item label="公告标题" prop="noticeTitle">
          <el-input v-model="form.noticeTitle" />
        </el-form-item>
        <el-form-item label="公告内容" prop="noticeContent">
          <el-input v-model="form.noticeContent" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker v-model="form.publishTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="是否发布">
          <el-switch v-model="form.published" inline-prompt active-text="发布" inactive-text="草稿" />
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
import { createNotice, deleteNotice, queryNotices, updateNotice } from '@/api/system'
import type { NoticeView } from '@/types/auth'

const notices = ref<NoticeView[]>([])
const visible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const detailItem = ref<NoticeView | null>(null)
const keyword = ref('')
const statusFilter = ref('')
const sortBy = ref<'publishTime' | 'createdAt' | 'noticeTitle'>('publishTime')
const sortDirection = ref<'asc' | 'desc'>('desc')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const formRef = ref<FormInstance>()

const form = reactive({
  noticeTitle: '',
  noticeContent: '',
  published: false,
  publishTime: '',
})

const rules = reactive<FormRules>({
  noticeTitle: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  noticeContent: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
})

const publishedCount = computed(() => notices.value.filter((item) => item.published).length)
const draftCount = computed(() => notices.value.filter((item) => !item.published).length)

void load()

async function load() {
  const result = await queryNotices({
    keyword: keyword.value || undefined,
    published: statusFilter.value ? statusFilter.value === 'published' : undefined,
    page: page.value,
    size: size.value,
    sortBy: sortBy.value,
    sortDirection: sortDirection.value,
  })
  notices.value = result.records
  total.value = result.total
}

function handleSearch() {
  page.value = 1
  void load()
}

function resetSearch() {
  keyword.value = ''
  statusFilter.value = ''
  sortBy.value = 'publishTime'
  sortDirection.value = 'desc'
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

function openDetail(row: NoticeView) {
  detailItem.value = row
  detailVisible.value = true
}

function openNotice(row?: NoticeView) {
  editingId.value = row?.id ?? null
  Object.assign(form, {
    noticeTitle: row?.noticeTitle ?? '',
    noticeContent: row?.noticeContent ?? '',
    published: row?.published ?? false,
    publishTime: row?.publishTime ?? '',
  })
  visible.value = true
}

async function submit() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
  if (editingId.value) {
    await updateNotice(editingId.value, form)
    ElMessage.success('公告已更新')
  } else {
    await createNotice(form)
    ElMessage.success('公告已创建')
  }
  visible.value = false
  await load()
}

async function removeNotice(id: number) {
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除确认', { type: 'warning' })
  await deleteNotice(id)
  ElMessage.success('公告已删除')
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
