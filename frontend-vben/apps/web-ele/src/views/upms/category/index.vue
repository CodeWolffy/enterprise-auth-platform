<script lang="ts" setup>
import type { CategoryAnalysis, CategoryOption } from '#/types/system';

import { computed, defineAsyncComponent, nextTick, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useTablePreferences } from '@vben/hooks';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { getInstanceByDom, init, use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import {
  ElButton,
  ElCheckbox,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPopover,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus';

import { delObj, getAnalysis, getOptions } from '#/api/upms/category';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';

use([BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));

const activeTab = ref<'config' | 'dict'>('dict');
const loading = ref(false);
const tableData = ref<CategoryOption[]>([]);
const showSearch = ref(true);
const refForm = ref();
const formMounted = ref(false);
const queryRef = ref();
const keyword = ref('');

const analysisVisible = ref(false);
const analysis = ref<CategoryAnalysis | null>(null);
const trendChartRef = ref<HTMLElement | null>(null);

const categoryTablePrefs = useTablePreferences('table:system-categories', [
  { key: 'code', label: '分类编码', width: 160 },
  { key: 'name', label: '分类名称', width: 180 },
  { key: 'matchers', label: '匹配规则', width: 320 },
  { key: 'actions', label: '操作', width: 180 },
]);

const targetTypeLabel = computed(() =>
  activeTab.value === 'dict' ? '字典分类' : '参数分类',
);

const filteredTableData = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  if (!value) {
    return tableData.value;
  }
  return tableData.value.filter((item) =>
    [item.code, item.name, ...item.matchers].some((text) =>
      text.toLowerCase().includes(value),
    ),
  );
});

const ruleSummary = computed(() => {
  if (keyword.value.trim()) {
    return `共 ${tableData.value.length} 条规则，命中 ${filteredTableData.value.length} 条`;
  }
  return `当前 ${targetTypeLabel.value}共 ${tableData.value.length} 条规则`;
});

const initPage = async () => {
  loading.value = true;
  try {
    const data = await getOptions(activeTab.value);
    tableData.value = data as CategoryOption[];
  } catch {
    ElMessage.error('分类数据加载失败');
  } finally {
    loading.value = false;
  }
};

const openForm = (row?: CategoryOption) => {
  formMounted.value = true;
  void invokeWhenComponentReady(refForm, (form: any) =>
    form.initForm(activeTab.value, row),
  );
};

const add = () => openForm();

const edit = (row: CategoryOption) => openForm(row);

const asCategoryOption = (row: unknown) => row as CategoryOption;

const del = (code: string) => {
  ElMessageBox.confirm('此操作将删除该分类，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(activeTab.value, code)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

const handleTabChange = () => {
  initPage();
};

const openAnalysis = async (row: CategoryOption) => {
  try {
    analysis.value = await getAnalysis(activeTab.value, row.code);
    analysisVisible.value = true;
    await nextTick();
    renderTrendChart();
  } catch {
    ElMessage.error('分析数据加载失败');
  }
};

const renderTrendChart = () => {
  if (!trendChartRef.value || !analysis.value) {
    return;
  }
  const existing = getInstanceByDom(trendChartRef.value);
  const chart = existing ?? init(trendChartRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 24, right: 16, top: 24, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: analysis.value.trend.map((item) => item.date.slice(5)),
      axisTick: { alignWithLabel: true },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
    },
    series: [
      {
        type: 'bar',
        data: analysis.value.trend.map((item) => item.count),
        itemStyle: {
          color: '#1677ff',
          borderRadius: [6, 6, 0, 0],
        },
      },
    ],
  });
};

const resetQuery = () => {
  queryRef.value?.resetFields();
  keyword.value = '';
};

initPage();
</script>

<template>
  <Page auto-content-height>
    <div class="category-page">
      <!-- 搜索 -->
      <ElForm ref="queryRef" :inline="true" v-show="showSearch">
        <ElFormItem label="分类类型">
          <ElSelect
            v-model="activeTab"
            style="width: 180px"
            @change="handleTabChange"
          >
            <ElOption label="字典分类" value="dict" />
            <ElOption label="参数分类" value="config" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="关键字" prop="keyword">
          <ElInput v-model="keyword" clearable placeholder="请输入编码或名称" />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>

      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            v-access:code="'upms:syscategory:add'"
            type="primary"
            @click="add"
            :icon="Plus"
          >
            新增分类
          </ElButton>
          <span class="rule-summary">{{ ruleSummary }}</span>
        </div>
        <div>
          <ElPopover placement="bottom-end" width="240" trigger="click">
            <template #reference>
              <ElButton>列显示</ElButton>
            </template>
            <div class="column-chooser">
              <ElCheckbox
                v-for="item in categoryTablePrefs.columns"
                :key="item.key"
                :model-value="categoryTablePrefs.visibleColumnMap[item.key]"
                @change="
                  (value) =>
                    categoryTablePrefs.setColumnVisible(
                      item.key,
                      Boolean(value),
                    )
                "
              >
                {{ item.label }}
              </ElCheckbox>
            </div>
          </ElPopover>
          <ElButton @click="categoryTablePrefs.reset()"> 恢复默认 </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>

      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        :data="filteredTableData"
        border
        @header-dragend="
          (newWidth: number, _oldWidth: number, column: any) => {
            const key = String(column.columnKey || column.property || '');
            if (key) {
              categoryTablePrefs.setColumnWidth(key, newWidth);
            }
          }
        "
      >
        <ElTableColumn
          v-if="categoryTablePrefs.visibleColumnMap.code"
          column-key="code"
          prop="code"
          label="分类编码"
          :min-width="160"
          :width="categoryTablePrefs.getColumnWidth('code')"
        />
        <ElTableColumn
          v-if="categoryTablePrefs.visibleColumnMap.name"
          column-key="name"
          prop="name"
          label="分类名称"
          :min-width="180"
          :width="categoryTablePrefs.getColumnWidth('name')"
        />
        <ElTableColumn
          v-if="categoryTablePrefs.visibleColumnMap.matchers"
          column-key="matchers"
          label="匹配规则"
          :min-width="320"
          :width="categoryTablePrefs.getColumnWidth('matchers')"
        >
          <template #default="{ row }">
            <ElTag
              v-for="matcher in row.matchers"
              :key="matcher"
              class="scope-tag"
              size="small"
            >
              {{ matcher }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          v-if="categoryTablePrefs.visibleColumnMap.actions"
          column-key="actions"
          fixed="right"
          label="操作"
          :width="categoryTablePrefs.getColumnWidth('actions') || 180"
        >
          <template #default="{ row }">
            <ElButton
              v-access:code="'upms:syscategory:get'"
              link
              type="primary"
              @click="openAnalysis(asCategoryOption(row))"
            >
              分析
            </ElButton>
            <ElButton
              v-access:code="'upms:syscategory:edit'"
              link
              type="primary"
              @click="edit(asCategoryOption(row))"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              v-access:code="'upms:syscategory:del'"
              link
              type="danger"
              @click="del(row.code)"
              :icon="Delete"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无分类数据" />
        </template>
      </ElTable>

      <Form v-if="formMounted" ref="refForm" @init-page="initPage" />

      <!-- 分析抽屉 -->
      <ElDrawer v-model="analysisVisible" title="分类引用分析" size="720px">
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
            <ElDescriptionsItem label="匹配规则" :span="2">
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
            <ElTable v-else :data="analysis.recentAudits" stripe size="small">
              <ElTableColumn
                prop="eventType"
                label="事件类型"
                min-width="180"
              />
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
    </div>
  </Page>
</template>

<style scoped lang="scss">
.category-page {
  box-sizing: border-box;
  min-height: 100%;
  padding: 8px;
  background: hsl(var(--card));
  border-radius: 8px;
}

.hx-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.hx-table-toolbar > div {
  display: flex;
  gap: 8px;
  align-items: center;
}

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
