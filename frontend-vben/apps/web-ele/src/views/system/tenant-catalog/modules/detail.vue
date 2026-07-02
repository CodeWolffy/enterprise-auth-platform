<script setup lang="ts">
import { ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import {
  ElAlert,
  ElDescriptions,
  ElDescriptionsItem,
  ElTag,
} from 'element-plus';

import { queryTenantPackageImpact } from '#/api/upms/tenant-package';
import type {
  TenantPackageImpactView,
  TenantPackageView,
} from '#/types/tenant';

const detailPackage = ref<TenantPackageView | null>(null);
const detailImpact = ref<TenantPackageImpactView | null>(null);
const detailLoading = ref(false);

function appKeys(value?: string | null) {
  if (!value?.trim()) return [];
  return value
    .split(/[,;\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

const [Drawer, drawerApi] = useVbenDrawer({
  onOpenChange(isOpen) {
    if (!isOpen) return;
    const data = drawerApi.getData<TenantPackageView>();
    detailPackage.value = data;
    detailImpact.value = null;
    if (data?.id) {
      detailLoading.value = true;
      queryTenantPackageImpact(data.id)
        .then((res) => {
          detailImpact.value = res;
        })
        .finally(() => {
          detailLoading.value = false;
        });
    }
  },
});
</script>

<template>
  <Drawer class="w-full max-w-[560px]" title="套餐详情">
    <div v-if="detailPackage" class="flex flex-col gap-4 px-4">
      <ElDescriptions :column="1" border>
        <ElDescriptionsItem label="套餐编码">
          {{ detailPackage.packageCode }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="套餐名称">
          {{ detailPackage.packageName }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="副标题">
          {{ detailPackage.subtitle || '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="原价（元）">
          {{ detailPackage.originalPrice ?? '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="销售价（元）">
          {{ detailPackage.salesPrice ?? '—' }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="应用标识">
          <div class="flex flex-wrap gap-1">
            <ElTag
              v-for="key in appKeys(detailPackage.appKey)"
              :key="key"
              effect="plain"
            >
              {{ key }}
            </ElTag>
            <span v-if="!appKeys(detailPackage.appKey).length" class="text-muted-foreground">未配置</span>
          </div>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="排序">
          {{ detailPackage.orderNo ?? 0 }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="状态">
          <ElTag :type="detailPackage.status === '0' ? 'success' : 'info'">
            {{ detailPackage.status === '0' ? '正常' : '停用' }}
          </ElTag>
        </ElDescriptionsItem>
        <ElDescriptionsItem label="引用租户">
          {{
            detailImpact?.referencedTenantCount ??
            detailPackage.referencedTenantCount ??
            0
          }}
        </ElDescriptionsItem>
        <ElDescriptionsItem label="描述">
          {{ detailPackage.descriptionMd || '—' }}
        </ElDescriptionsItem>
      </ElDescriptions>

      <div v-loading="detailLoading" class="flex flex-col gap-3 mt-2">
        <h3 class="text-base font-semibold m-0">影响分析</h3>

        <ElAlert
          v-for="rule in detailImpact?.rules || []"
          :key="rule.ruleCode"
          :title="rule.message"
          :type="rule.level === 'ERROR' ? 'error' : 'warning'"
          show-icon
          :closable="false"
        />

        <div
          v-if="detailImpact?.referencedTenantIds?.length"
          class="flex flex-wrap gap-1"
        >
          <ElTag
            v-for="tenantId in detailImpact.referencedTenantIds"
            :key="tenantId"
          >
            {{ tenantId }}
          </ElTag>
        </div>

        <ul
          v-if="detailImpact?.recommendedActions?.length"
          class="list-disc pl-5 m-0 text-foreground"
        >
          <li v-for="item in detailImpact.recommendedActions" :key="item">
            {{ item }}
          </li>
        </ul>
      </div>
    </div>
  </Drawer>
</template>