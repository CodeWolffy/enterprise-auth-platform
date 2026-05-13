<template>
  <el-form :inline="true" class="advanced-search content-toolbar" @submit.prevent="$emit('search')">
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
  width: 100%;

  &__fields {
    display: flex;
    flex-wrap: wrap;
    flex: 1 1 520px;
    gap: 12px 16px;
    min-width: 0;

    :deep(.el-form-item) {
      margin-bottom: 0;
      margin-right: 0;
    }
  }

  &__actions {
    display: flex;
    align-items: flex-end;
    margin-left: auto;

    :deep(.el-form-item) {
      margin-bottom: 0;
      margin-right: 0;
    }
  }
}

@media (max-width: 860px) {
  .advanced-search {
    display: grid;

    &__fields,
    &__actions {
      width: 100%;
    }

    &__fields {
      display: grid;
    }
  }
}
</style>
