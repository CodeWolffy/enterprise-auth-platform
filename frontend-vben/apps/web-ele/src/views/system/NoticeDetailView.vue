<script setup lang="ts">
import type { NoticeView } from '#/types/system';

import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { getPublishedNotice } from '#/api/system';
import RichTextViewer from '#/components/rich-text-viewer/index.vue';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const notice = ref<NoticeView | null>(null);

onMounted(async () => {
  notice.value = await getPublishedNotice(route.params.id as string);
});

const goBack = () => {
  router.back();
};
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div class="notice-detail-page">
        <el-page-header title="返回" @back="goBack">
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
              <span>{{ notice.publishTime }}</span>
              <span>发布人：{{ notice.createdBy }}</span>
            </div>
            <h1>{{ notice.noticeTitle }}</h1>
          </header>
          <RichTextViewer :content="notice.noticeContent" />
        </section>

        <el-empty v-else description="公告不存在或尚未发布" />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.notice-detail-page {
  padding: 24px;

  .page-header__title {
    font-size: 18px;
    font-weight: 600;
  }

  .notice-detail-loading {
    padding: 24px;
    margin-top: 24px;
    background: var(--el-bg-color);
    border-radius: 8px;
  }

  .notice-detail-card {
    padding: 32px;
    margin-top: 24px;
    background: var(--el-bg-color);
    border-radius: 8px;

    header {
      padding-bottom: 24px;
      margin-bottom: 24px;
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
    gap: 16px;
    align-items: center;
    color: var(--el-text-color-secondary);
  }
}
</style>
