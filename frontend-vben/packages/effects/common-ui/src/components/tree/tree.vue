<script setup lang="ts">
import type { PropType } from 'vue';

import type { TreeProps } from '@vben-core/shadcn-ui';

import { Inbox } from '@vben/icons';
import { $t } from '@vben/locales';

import { VbenTree } from '@vben-core/shadcn-ui';

const props = defineProps({
  allowClear: { type: Boolean, default: false },
  autoCheckParent: { type: Boolean, default: true },
  bordered: { type: Boolean, default: false },
  checkStrictly: { type: Boolean, default: false },
  childrenField: { type: String, default: 'children' },
  defaultExpandedKeys: {
    type: Array as PropType<TreeProps['defaultExpandedKeys']>,
    default: () => [],
  },
  defaultExpandedLevel: { type: Number, default: 0 },
  defaultValue: {
    type: [Array, Number, String] as PropType<TreeProps['defaultValue']>,
    default: undefined,
  },
  disabled: { type: Boolean, default: false },
  disabledField: { type: String, default: 'disabled' },
  getNodeClass: {
    type: Function as PropType<TreeProps['getNodeClass']>,
    default: undefined,
  },
  iconField: { type: String, default: 'icon' },
  labelField: { type: String, default: 'label' },
  multiple: { type: Boolean, default: false },
  selectAllLabel: { type: String, default: undefined },
  showIcon: { type: Boolean, default: true },
  transition: { type: Boolean, default: true },
  treeData: {
    type: Array as PropType<TreeProps['treeData']>,
    default: () => [],
  },
  valueField: { type: String, default: 'value' },
});
</script>

<template>
  <VbenTree v-if="props.treeData?.length > 0" v-bind="props">
    <template v-for="(_, key) in $slots" :key="key" #[key]="slotProps">
      <slot :name="key" v-bind="slotProps"> </slot>
    </template>
  </VbenTree>
  <div
    v-else
    class="flex-col-center cursor-pointer rounded-lg border p-10 text-sm font-medium text-muted-foreground"
  >
    <Inbox class="size-10" />
    <div class="mt-1">{{ $t('common.noData') }}</div>
  </div>
</template>
