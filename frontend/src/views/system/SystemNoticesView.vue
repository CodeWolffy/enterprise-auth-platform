<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">公告</span>
        <strong>{{ total }}</strong>
        <span>当前筛选条件下的公告总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">已发布</span>
        <strong>{{ publishedCount }}</strong>
        <span>当前页已发布公告数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">草稿</span>
        <strong>{{ draftCount }}</strong>
        <span>当前页草稿数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">公告</span>
          <h3>公告管理</h3>
        </div>
        <el-button v-permission="'system:write'" type="primary" @click="openNotice()">新增公告</el-button>
      </div>

      <AdvancedSearch @search="handleSearch" @reset="resetSearch">
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
        <el-form-item label="工作流状态">
          <el-select v-model="workflowStatus" placeholder="全部" clearable style="width: 160px">
            <el-option label="全部" value="" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待发布" value="SCHEDULED" />
            <el-option label="已发布" value="PUBLISHED" />
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
      </AdvancedSearch>

      <div class="table-tools">
        <el-radio-group v-model="noticeTablePrefs.density" size="small">
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
              v-for="item in noticeTablePrefs.columns"
              :key="item.key"
              :model-value="noticeTablePrefs.visibleColumnMap[item.key]"
              @change="(value: boolean) => noticeTablePrefs.setColumnVisible(item.key, value)"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </el-popover>
        <el-button size="small" @click="noticeTablePrefs.reset()">恢复默认</el-button>
      </div>

      <el-result v-if="loadError" icon="error" title="加载失败" :sub-title="loadError" class="panel-result">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <el-table
        v-else
        v-loading="loading"
        :data="notices"
        stripe
        :class="`table-density-${noticeTablePrefs.density}`"
        @header-dragend="onNoticeHeaderDragEnd"
      >
        <el-table-column
          v-if="noticeTablePrefs.visibleColumnMap.noticeTitle"
          column-key="noticeTitle"
          prop="noticeTitle"
          label="标题"
          min-width="180"
          :width="noticeTablePrefs.getColumnWidth('noticeTitle')"
        />
        <el-table-column
          v-if="noticeTablePrefs.visibleColumnMap.noticeContent"
          column-key="noticeContent"
          prop="noticeContent"
          label="内容"
          min-width="260"
          show-overflow-tooltip
          :width="noticeTablePrefs.getColumnWidth('noticeContent')"
        />
        <el-table-column
          v-if="noticeTablePrefs.visibleColumnMap.published"
          column-key="published"
          label="发布状态"
          min-width="110"
          :width="noticeTablePrefs.getColumnWidth('published')"
        >
          <template #default="{ row }">
            <el-tag :type="row.published ? 'success' : 'info'">{{ row.published ? '已发布' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="noticeTablePrefs.visibleColumnMap.workflowStatus"
          column-key="workflowStatus"
          prop="workflowStatus"
          label="工作流状态"
          min-width="120"
          :width="noticeTablePrefs.getColumnWidth('workflowStatus')"
        />
        <el-table-column
          v-if="noticeTablePrefs.visibleColumnMap.publishTime"
          column-key="publishTime"
          label="发布时间"
          min-width="180"
          :width="noticeTablePrefs.getColumnWidth('publishTime')"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.publishTime) }}
          </template>
        </el-table-column>
        <el-table-column
          v-if="noticeTablePrefs.visibleColumnMap.createdBy"
          column-key="createdBy"
          prop="createdBy"
          label="创建人"
          min-width="120"
          :width="noticeTablePrefs.getColumnWidth('createdBy')"
        />
        <el-table-column
          v-if="noticeTablePrefs.visibleColumnMap.actions"
          column-key="actions"
          fixed="right"
          label="操作"
          :width="noticeTablePrefs.getColumnWidth('actions') || 220"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-permission="'system:write'" link type="primary" @click="openNotice(row)">编辑</el-button>
            <el-button v-permission="'system:write'" link type="danger" @click="removeNotice(row.id)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无公告数据" />
        </template>
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
        <el-descriptions :column="2" border class="drawer-section drawer-section--overview">
          <el-descriptions-item label="标题" :span="2">{{ detailItem.noticeTitle }}</el-descriptions-item>
          <el-descriptions-item label="发布状态">{{ detailItem.published ? '已发布' : '草稿' }}</el-descriptions-item>
          <el-descriptions-item label="工作流状态">{{ detailItem.workflowStatus }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ formatDateTime(detailItem.publishTime) }}</el-descriptions-item>
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
          <el-date-picker v-model="form.publishTime" type="datetime" />
        </el-form-item>
        <el-form-item label="是否发布">
          <el-switch v-model="form.published" inline-prompt active-text="发布" inactive-text="草稿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-permission="'system:write'" type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import type { FormInstance, FormRules } from 'element-plus'
import { createNotice, deleteNotice, queryNotices, updateNotice } from '@/api/modules'
import { useTablePreferences } from '@/composables/useTablePreferences'
import type { NoticeView } from '@/types/auth'
import { formatDateTime, toDate, toEpochMs } from '@/utils/datetime'

const notices = ref<NoticeView[]>([])
const loading = ref(false)
const loadError = ref('')
const visible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const detailItem = ref<NoticeView | null>(null)
const keyword = ref('')
const statusFilter = ref('')
const workflowStatus = ref('')
const sortBy = ref<'publishTime' | 'createdAt' | 'noticeTitle'>('publishTime')
const sortDirection = ref<'asc' | 'desc'>('desc')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const formRef = ref<FormInstance>()

const noticeTablePrefs = useTablePreferences('table:system-notices', [
  { key: 'noticeTitle', label: '标题', width: 180 },
  { key: 'noticeContent', label: '内容', width: 260 },
  { key: 'published', label: '发布状态', width: 110 },
  { key: 'workflowStatus', label: '工作流状态', width: 120 },
  { key: 'publishTime', label: '发布时间', width: 180 },
  { key: 'createdBy', label: '创建人', width: 120 },
  { key: 'actions', label: '操作', width: 220 },
])

const form = reactive({
  noticeTitle: '',
  noticeContent: '',
  published: false,
  publishTime: null as Date | null,
})

const rules = reactive<FormRules>({
  noticeTitle: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  noticeContent: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
})

const publishedCount = computed(() => notices.value.filter((item) => item.published).length)
const draftCount = computed(() => notices.value.filter((item) => !item.published).length)

void load()

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await queryNotices({
      keyword: keyword.value || undefined,
      published: statusFilter.value ? statusFilter.value === 'published' : undefined,
      workflowStatus: (workflowStatus.value || undefined) as 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | undefined,
      page: page.value,
      size: size.value,
      sortBy: sortBy.value,
      sortDirection: sortDirection.value,
    })
    notices.value = result.records
    total.value = result.total
  } catch {
    notices.value = []
    total.value = 0
    loadError.value = '公告数据加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  void load()
}

function resetSearch() {
  keyword.value = ''
  statusFilter.value = ''
  workflowStatus.value = ''
  sortBy.value = 'publishTime'
  sortDirection.value = 'desc'
  page.value = 1
  void load()
}

function handleSizeChange(value: number) {
  size.value = value
  page.value = 1
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
    publishTime: toDate(row?.publishTime),
  })
  visible.value = true
}

async function submit() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
  const payload = {
    noticeTitle: form.noticeTitle,
    noticeContent: form.noticeContent,
    published: form.published,
    publishTime: toEpochMs(form.publishTime),
  }
  if (editingId.value) {
    await updateNotice(editingId.value, payload)
    ElMessage.success('公告已更新')
  } else {
    await createNotice(payload)
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

function onNoticeHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  noticeTablePrefs.setColumnWidth(key, newWidth)
}
</script>
