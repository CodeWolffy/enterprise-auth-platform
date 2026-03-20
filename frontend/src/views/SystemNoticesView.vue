<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Notices</span>
        <strong>{{ filteredNotices.length }}</strong>
        <span>当前筛选条件下公告总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Published</span>
        <strong>{{ publishedCount }}</strong>
        <span>已发布公告数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Draft</span>
        <strong>{{ draftCount }}</strong>
        <span>草稿公告数量</span>
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

      <el-form :inline="true" class="toolbar-inline" @submit.prevent>
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
      </el-form>

      <el-table :data="filteredNotices" stripe>
        <el-table-column prop="noticeTitle" label="标题" min-width="180" />
        <el-table-column prop="noticeContent" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="发布状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.published ? 'success' : 'info'">{{ row.published ? '已发布' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" min-width="180" />
        <el-table-column prop="createdBy" label="创建人" min-width="120" />
        <el-table-column fixed="right" label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openNotice(row)">编辑</el-button>
            <el-button link type="danger" @click="removeNotice(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑公告' : '新增公告'" width="680px">
      <el-form label-position="top">
        <el-form-item label="公告标题">
          <el-input v-model="form.noticeTitle" />
        </el-form-item>
        <el-form-item label="公告内容">
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
import { createNotice, deleteNotice, queryNotices, updateNotice } from '@/api/system'
import type { NoticeView } from '@/types/auth'

const notices = ref<NoticeView[]>([])
const visible = ref(false)
const editingId = ref<number | null>(null)
const keyword = ref('')
const statusFilter = ref('')

const form = reactive({
  noticeTitle: '',
  noticeContent: '',
  published: false,
  publishTime: '',
})

const filteredNotices = computed(() =>
  notices.value.filter((item) => {
    const normalizedKeyword = keyword.value.trim().toLowerCase()
    const matchesKeyword =
      !normalizedKeyword ||
      [item.noticeTitle, item.noticeContent].some((value) => value.toLowerCase().includes(normalizedKeyword))
    const matchesStatus =
      !statusFilter.value ||
      (statusFilter.value === 'published' ? item.published : !item.published)
    return matchesKeyword && matchesStatus
  }),
)

const publishedCount = computed(() => filteredNotices.value.filter((item) => item.published).length)
const draftCount = computed(() => filteredNotices.value.filter((item) => !item.published).length)

void load()

async function load() {
  notices.value = await queryNotices()
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
