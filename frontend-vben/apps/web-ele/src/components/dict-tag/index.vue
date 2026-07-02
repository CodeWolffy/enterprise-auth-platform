<script setup lang="ts" name="dict-tag">
import { computed } from 'vue';

import { ElTag } from 'element-plus';

const props = defineProps({
  options: {
    type: Array as any,
    default: null,
  },
  value: {
    type: [String, Array],
    default: '',
  },
});

const values = computed(() => {
  if (props.value !== null && props.value !== 'undefined') {
    return Array.isArray(props.value) ? props.value : [props.value];
  } else {
    return [];
  }
});
</script>

<template>
  <div>
    <template v-for="(item, index) in options" :key="index">
      <span
        v-if="
          values.includes(item.value || item) &&
          (item.showClass === '' || item.showClass === 'default')
        "
        >{{ item.label || item }}
      </span>
      <ElTag
        v-else-if="values.includes(item.value || item)"
        :index="index"
        :type="item.showClass"
        :disable-transitions="true"
      >
        {{ item.label || item }}
      </ElTag>
    </template>
  </div>
</template>

<style scoped></style>
