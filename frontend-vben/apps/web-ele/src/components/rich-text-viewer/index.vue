<script setup lang="ts" name="rich-text-viewer">
import { computed } from 'vue';

import { ElEmpty } from 'element-plus';

import { hasMeaningfulRichText, sanitizeRichText } from '#/utils/rich-text';

interface Props {
  /** 富文本 HTML 内容 */
  content?: null | string;
  /** 空内容提示 */
  emptyText?: string;
  /** 是否使用卡片包裹 */
  card?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  card: false,
  content: '',
  emptyText: '暂无内容',
});

const safeHtml = computed(() => sanitizeRichText(props.content));
const hasContent = computed(() => hasMeaningfulRichText(props.content));
</script>

<template>
  <div class="rich-text-viewer" :class="{ 'is-card': card }">
    <ElEmpty v-if="!hasContent" :description="emptyText" />
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-else class="rich-text-viewer__content" v-html="safeHtml"></div>
  </div>
</template>

<style scoped lang="scss">
.rich-text-viewer {
  width: 100%;
}

.rich-text-viewer.is-card {
  padding: 20px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.rich-text-viewer__content {
  font-size: 15px;
  line-height: 1.8;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin: 20px 0 12px;
    font-weight: 600;
    line-height: 1.4;
    color: var(--el-text-color-primary);
  }

  :deep(h1) {
    font-size: 24px;
  }

  :deep(h2) {
    font-size: 20px;
  }

  :deep(h3) {
    font-size: 18px;
  }

  :deep(p) {
    margin: 0 0 12px;
  }

  :deep(a) {
    color: var(--el-color-primary);
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  :deep(img) {
    display: block;
    max-width: 100%;
    height: auto;
    margin: 12px 0;
    border-radius: var(--el-border-radius-base);
  }

  :deep(ul),
  :deep(ol) {
    padding: 0;
    margin: 0 0 12px 20px;
  }

  :deep(li) {
    margin-bottom: 6px;
  }

  :deep(blockquote) {
    padding: 12px 16px;
    margin: 14px 0;
    color: var(--el-text-color-regular);
    background: var(--el-fill-color-light);
    border-left: 4px solid var(--el-color-primary);
    border-radius: 0 var(--el-border-radius-base) var(--el-border-radius-base) 0;
  }

  :deep(pre) {
    padding: 14px;
    margin: 14px 0;
    overflow-x: auto;
    font-family:
      ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 13px;
    line-height: 1.6;
    color: var(--el-text-color-primary);
    background: var(--el-fill-color-dark);
    border-radius: var(--el-border-radius-base);
  }

  :deep(code) {
    padding: 2px 6px;
    font-family:
      ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 13px;
    color: var(--el-color-danger);
    background: var(--el-fill-color);
    border-radius: var(--el-border-radius-small);
  }

  :deep(pre code) {
    padding: 0;
    color: inherit;
    background: transparent;
  }

  :deep(table) {
    width: 100%;
    margin: 14px 0;
    table-layout: fixed;
    border-collapse: collapse;
  }

  :deep(th),
  :deep(td) {
    padding: 8px 12px;
    vertical-align: top;
    border: 1px solid var(--el-border-color);
  }

  :deep(th) {
    font-weight: 600;
    background: var(--el-fill-color-light);
  }

  :deep(hr) {
    margin: 20px 0;
    border: 0;
    border-top: 1px solid var(--el-border-color);
  }

  :deep(> *:last-child) {
    margin-bottom: 0;
  }
}
</style>
