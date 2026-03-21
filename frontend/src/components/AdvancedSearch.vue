<template>
  <el-form :inline="true" class="advanced-search" @submit.prevent="$emit('search')">
    <div class="advanced-search__fields">
      <slot></slot>
    </div>
    <div class="advanced-search__actions">
      <el-form-item>
        <el-button type="primary" @click="$emit('search')">查询</el-button>
        <el-button @click="$emit('reset')">重置</el-button>
        <slot name="extra-actions"></slot>
      </el-form-item>
    </div>
  </el-form>
</template>

<script setup lang="ts">
defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
}>()
</script>

<style scoped lang="scss">
.advanced-search {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  background-color: var(--el-fill-color-blank);
  padding: 16px 20px;
  border-radius: 8px;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);

  &__fields {
    display: flex;
    flex-wrap: wrap;
    flex: 1;
    gap: 16px;

    :deep(.el-form-item) {
      margin-bottom: 0;
      margin-right: 0;
    }
  }

  &__actions {
    display: flex;
    align-items: flex-end;

    :deep(.el-form-item) {
      margin-bottom: 0;
      margin-right: 0;
    }
  }
}
</style>
