<template>
  <div class="notice-detail-page">
    <el-page-header title="返回" @back="router.back()">
      <template #content>
        <span class="page-header__title">公告详情</span>
      </template>
    </el-page-header>

    <section v-if="loading" class="notice-detail-loading">
      <el-skeleton :rows="10" animated />
    </section>

    <section v-else-if="notice" class="notice-detail-card">
      <header>
        <div class="notice-detail-card__meta">
          <el-tag type="success">已发布</el-tag>
          <span>{{ formatDateTime(notice.publishTime) }}</span>
          <span>发布人：{{ notice.createdBy }}</span>
        </div>
        <h1>{{ notice.noticeTitle }}</h1>
      </header>
      <div class="notice-rich-content" v-html="sanitizeRichText(notice.noticeContent)"></div>
    </section>

    <el-empty v-else description="公告不存在或尚未发布" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchPublishedNotice } from '@/api/modules/system'
import type { NoticeView } from '@/types/system'
import { formatDateTime } from '@/utils/datetime'
import { sanitizeRichText } from '@/utils/richText'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const notice = ref<NoticeView | null>(null)

onMounted(async () => {
  const id = Number(route.params.id)
  if (!Number.isFinite(id) || id <= 0) {
    ElMessage.error('公告 ID 无效')
    return
  }
  loading.value = true
  try {
    notice.value = await fetchPublishedNotice(id)
  } catch {
    notice.value = null
  } finally {
    loading.value = false
  }
})
</script>

<style scoped lang="scss">
.notice-detail-page {
  padding: 24px;

  .page-header__title {
    font-size: 18px;
    font-weight: 600;
  }

  .notice-detail-loading {
    margin-top: 24px;
    padding: 24px;
    background: var(--el-bg-color);
    border-radius: 8px;
  }

  .notice-detail-card {
    margin-top: 24px;
    padding: 32px;
    background: var(--el-bg-color);
    border-radius: 8px;

    header {
      margin-bottom: 24px;
      padding-bottom: 24px;
      border-bottom: 1px solid var(--el-border-color-light);

      h1 {
        margin: 16px 0 0;
        font-size: 24px;
        line-height: 1.4;
      }
    }
  }

  .notice-detail-card__meta {
    display: flex;
    align-items: center;
    gap: 16px;
    color: var(--el-text-color-secondary);
    font-size: 14px;
  }
}
</style>
