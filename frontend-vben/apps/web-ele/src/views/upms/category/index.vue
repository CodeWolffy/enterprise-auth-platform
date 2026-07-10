<script lang="ts" setup>
import { PERMS } from '#/constants/permissions';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { CategoryAnalysis, CategoryOption } from '#/types/system';

import { computed, defineAsyncComponent, nextTick, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { getInstanceByDom, init, use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { delObj, getAnalysis, getOptions } from '#/api/upms/category';
import { formatDateTime } from '#/utils/datetime';
import { invokeWhenComponentReady } from '#/utils/component-ready';

import { useColumns, useGridFormSchema } from './data';

use([BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

const Form = defineAsyncComponent(() => import('./form.vue'));

const activeTab = ref<'config' | 'dict'>('dict');
const ruleCount = ref(0);
const matchedRuleCount = ref(0);
const hasKeyword = ref(false);
const formRef = ref();
const formMounted = ref(false);
const analysisVisible = ref(false);
const analysis = ref<CategoryAnalysis | null>(null);
const trendChartRef = ref<HTMLElement | null>(null);

const targetTypeLabel = computed(() =>
  activeTab.value === 'dict' ? '字典分类' : '参数分类',
);

const ruleSummary = computed(() =>
  hasKeyword.value
    ? `共 ${ruleCount.value} 条规则，命中 ${matchedRuleCount.value} 条`
    : `当前 ${targetTypeLabel.value}共 ${ruleCount.value} 条规则`,
);

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: useGridFormSchema(),
    submitOnChange: false,
  },
  gridOptions: {
    columns: useColumns(),
    height: 'auto',
    keepSource: true,
    pagerConfig: {
      enabled: false,
    },
    proxyConfig: {
      ajax: {
        query: async (_params, formValues) => {
          activeTab.value =
            formValues.targetType === 'config' ? 'config' : 'dict';
          const records = (await getOptions(
            activeTab.value,
          )) as CategoryOption[];
          const keyword = String(formValues.keyword ?? '')
            .trim()
            .toLowerCase();
          const filtered = keyword
            ? records.filter((item) =>
                [item.code, item.name, ...item.matchers].some((text) =>
                  text.toLowerCase().includes(keyword),
                ),
              )
            : records;
          ruleCount.value = records.length;
          matchedRuleCount.value = filtered.length;
          hasKeyword.value = Boolean(keyword);
          return filtered;
        },
      },
    },
    rowConfig: {
      keyField: 'code',
    },
    toolbarConfig: {
      custom: true,
      refresh: true,
      refreshOptions: { code: 'query' },
      search: true,
      zoom: false,
    },
  } as VxeTableGridOptions<CategoryOption>,
});

function onRefresh() {
  gridApi.query();
}

function openForm(row?: CategoryOption) {
  formMounted.value = true;
  void invokeWhenComponentReady(formRef, (form: any) =>
    form.initForm(activeTab.value, row),
  );
}

async function onDelete(row: CategoryOption) {
  try {
    await ElMessageBox.confirm('此操作将删除该分类，是否继续?', '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确认',
      type: 'warning',
    });
    await delObj(activeTab.value, row.code);
    ElMessage.success('删除成功');
    onRefresh();
  } catch {
    // Cancelled confirmations require no further action.
  }
}

async function openAnalysis(row: CategoryOption) {
  try {
    analysis.value = await getAnalysis(activeTab.value, row.code);
    analysisVisible.value = true;
    await nextTick();
    renderTrendChart();
  } catch {
    ElMessage.error('分析数据加载失败');
  }
}

function renderTrendChart() {
  if (!trendChartRef.value || !analysis.value) return;
  const chart =
    getInstanceByDom(trendChartRef.value) ?? init(trendChartRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 24, right: 16, top: 24, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: analysis.value.trend.map((item) => item.date.slice(5)),
      axisTick: { alignWithLabel: true },
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        type: 'bar',
        data: analysis.value.trend.map((item) => item.count),
        itemStyle: { color: '#1677ff', borderRadius: [6, 6, 0, 0] },
      },
    ],
  });
}
</script>

<template>
  <Page auto-content-height>
    <Form v-if="formMounted" ref="formRef" @init-page="onRefresh" />

    <Grid>
      <template #toolbar-actions>
        <span class="rule-summary">{{ ruleSummary }}</span>
      </template>

      <template #toolbar-tools>
        <ElButton
          v-access:code="PERMS.upms.category.add"
          type="primary"
          @click="openForm()"
        >
          <Plus class="size-5" />
          新增分类
        </ElButton>
      </template>

      <template #matchers="{ row }">
        <ElTag
          v-for="matcher in row.matchers"
          :key="matcher"
          class="scope-tag"
          size="small"
        >
          {{ matcher }}
        </ElTag>
      </template>

      <template #operation="{ row }">
        <ElButton
          v-access:code="PERMS.upms.category.get"
          link
          type="primary"
          @click="openAnalysis(row)"
        >
          分析
        </ElButton>
        <ElButton
          v-access:code="PERMS.upms.category.edit"
          link
          type="primary"
          @click="openForm(row)"
        >
          修改
        </ElButton>
        <ElButton
          v-access:code="PERMS.upms.category.del"
          link
          type="danger"
          @click="onDelete(row)"
        >
          删除
        </ElButton>
      </template>
    </Grid>

    <ElDrawer v-model="analysisVisible" size="720px" title="分类引用分析">
      <template v-if="analysis">
        <ElDescriptions :column="2" border class="drawer-section">
          <ElDescriptionsItem label="分类编码">
            {{ analysis.code }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="分类名称">
            {{ analysis.name }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="目标类型">
            {{ analysis.targetType }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="引用数量">
            {{ analysis.referenceCount }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :span="2" label="匹配规则">
            <ElTag
              v-for="matcher in analysis.matchers"
              :key="matcher"
              class="scope-tag"
              size="small"
            >
              {{ matcher }}
            </ElTag>
          </ElDescriptionsItem>
        </ElDescriptions>

        <section class="analysis-section">
          <h4>七日影响趋势</h4>
          <div ref="trendChartRef" class="trend-chart"></div>
        </section>

        <section class="analysis-section">
          <h4>引用样例</h4>
          <ElEmpty
            v-if="analysis.sampleReferences.length === 0"
            description="暂无引用样例"
          />
          <ElTimeline v-else>
            <ElTimelineItem
              v-for="item in analysis.sampleReferences"
              :key="item"
            >
              {{ item }}
            </ElTimelineItem>
          </ElTimeline>
        </section>

        <section class="analysis-section">
          <h4>最近审计记录</h4>
          <ElEmpty
            v-if="analysis.recentAudits.length === 0"
            description="暂无分类变更审计"
          />
          <ElTable v-else :data="analysis.recentAudits" size="small" stripe>
            <ElTableColumn prop="eventType" label="事件类型" min-width="180" />
            <ElTableColumn prop="operator" label="操作人" min-width="120" />
            <ElTableColumn label="发生时间" min-width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.occurredAt) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="审计负载" min-width="260">
              <template #default="{ row }">
                <pre class="payload-pre">{{ row.payloadJson }}</pre>
              </template>
            </ElTableColumn>
          </ElTable>
        </section>
      </template>
    </ElDrawer>
  </Page>
</template>

<style scoped lang="scss">
.rule-summary {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.scope-tag {
  margin-right: 6px;
  margin-bottom: 6px;
}

.analysis-section {
  margin-top: 20px;

  h4 {
    margin: 0 0 12px;
    font-size: 14px;
    font-weight: 600;
  }
}

.trend-chart {
  width: 100%;
  height: 260px;
}

.payload-pre {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
