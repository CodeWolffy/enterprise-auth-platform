<script lang="ts" setup>
import type { CategoryAnalysis, CategoryOption } from '#/types/system';

import { computed, defineAsyncComponent, nextTick, ref, watch } from 'vue';

import { useTablePreferences } from '@vben/hooks';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import { BarChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { getInstanceByDom, init, use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import {
  ElButton,
  ElCard,
  ElCheckbox,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElPopover,
  ElRadioButton,
  ElRadioGroup,
  ElRow,
  ElStatistic,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
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

watch(
  () => queryRef.value?.model?.keyword,
  (val) => {
    if (val !== undefined) {
      keyword.value = val;
    }
  },
  { immediate: true },
);

const analysisVisible = ref(false);
const analysis = ref<CategoryAnalysis | null>(null);
const trendChartRef = ref<HTMLElement | null>(null);

const categoryTablePrefs = useTablePreferences('table:system-categories', [
  { key: 'code', label: '分类编码', width: 160 },
  { key: 'name', label: '分类名称', width: 180 },
  { key: 'matchers', label: '匹配规则', width: 320 },
  { key: 'actions', label: '操作', width: 180 },
]);

const matcherCount = computed(() =>
  tableData.value.reduce((sum, item) => sum + item.matchers.length, 0),
);

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
  queryRef.value.resetFields();
  keyword.value = '';
  initPage();
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 统计卡片 -->
      <ElRow :gutter="12" class="mb-4">
        <ElCol :span="12">
          <ElCard shadow="hover">
            <ElStatistic title="分类" :value="tableData.length">
              <template #suffix>
                <span class="text-xs text-gray-500">当前分类条目总数</span>
              </template>
            </ElStatistic>
          </ElCard>
        </ElCol>
        <ElCol :span="12">
          <ElCard shadow="hover">
            <ElStatistic title="匹配器" :value="matcherCount">
              <template #suffix>
                <span class="text-xs text-gray-500">当前页匹配规则总数</span>
              </template>
            </ElStatistic>
          </ElCard>
        </ElCol>
      </ElRow>

      <!-- 分类配置管理 -->
      <ElCard shadow="never">
        <div class="flex items-center justify-between mb-4">
          <div>
            <span class="eyebrow">分类</span>
            <h3>分类配置管理</h3>
          </div>
          <ElButton type="primary" @click="add">
            <template #icon>
              <Plus />
            </template>
            新增分类
          </ElButton>
        </div>

        <!-- 标签页 -->
        <ElTabs v-model="activeTab" @tab-change="handleTabChange">
          <ElTabPane label="字典分类" name="dict" />
          <ElTabPane label="参数分类" name="config" />
        </ElTabs>

        <!-- 搜索 -->
        <ElForm ref="queryRef" :inline="true" v-show="showSearch" class="mb-4">
          <ElFormItem label="关键字" prop="keyword">
            <ElInput
              v-model="keyword"
              clearable
              placeholder="请输入编码或名称"
            />
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" @click="initPage" :icon="Search">
              搜索
            </ElButton>
            <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
          </ElFormItem>
        </ElForm>

        <!-- 工具栏 -->
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2">
            <ElRadioGroup v-model="categoryTablePrefs.density" size="small">
              <ElRadioButton value="compact">紧凑</ElRadioButton>
              <ElRadioButton value="default">默认</ElRadioButton>
              <ElRadioButton value="comfortable">宽松</ElRadioButton>
            </ElRadioGroup>
            <ElPopover placement="bottom-end" width="240" trigger="click">
              <template #reference>
                <ElButton size="small">列显示</ElButton>
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
            <ElButton size="small" @click="categoryTablePrefs.reset()">
              恢复默认
            </ElButton>
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
          :data="tableData"
          stripe
          :class="`table-density-${categoryTablePrefs.density}`"
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
                link
                type="primary"
                @click="openAnalysis(asCategoryOption(row))"
              >
                分析
              </ElButton>
              <ElButton
                link
                type="primary"
                @click="edit(asCategoryOption(row))"
                :icon="Edit"
              >
                修改
              </ElButton>
              <ElButton
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
      </ElCard>

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
  </div>
</template>

<style scoped lang="scss">
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
