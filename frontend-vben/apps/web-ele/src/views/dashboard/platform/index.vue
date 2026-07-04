<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { Refresh } from '@element-plus/icons-vue';
import { LineChart } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components';
import * as echarts from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import {
  ElButton,
  ElCard,
  ElCol,
  ElRow,
  ElStatistic,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getStats } from '#/api/dashboard';
import { useAuthStore } from '#/store/auth';
import { formatDateTime, formatRelativeTime } from '#/utils/datetime';

echarts.use([
  LineChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);

const loading = ref(false);
const chartRef = ref<HTMLElement | null>(null);
let chartInstance: echarts.ECharts | null = null;

const stats = ref({
  userCount: 0,
  tenantCount: 0,
  roleCount: 0,
  menuCount: 0,
  fileCount: 0,
  storageBytes: 0,
  operationLogCount: 0,
  recentOperationLogCount: 0,
  todayLoginCount: 0,
  onlineUserCount: 0,
  todayOperationLogCount: 0,
  todayLoginFailedCount: 0,
  todayRiskEventCount: 0,
  dailyTrend: [] as Array<{
    date: string;
    loginCount: number;
    loginFailedCount: number;
    operationCount: number;
  }>,
  serviceHealth: [] as Array<{
    code: string;
    message: string;
    name: string;
    status: string;
  }>,
  recentAuditEvents: [] as Array<{
    clientIp: string;
    eventType: string;
    occurredAt: string;
    operator: string;
    tenantId: string;
  }>,
});

const authStore = useAuthStore();

const DATA_SCOPE_LABELS: Record<string, string> = {
  ALL: '全部数据',
  DEPT_AND_CHILD: '部门及子部门',
  DEPT: '本部门',
  SELF: '仅本人',
  CUSTOM: '自定义',
};

const dataScopeLabel = computed(() => {
  const dataScopeType = authStore.snapshot?.dataScopeType;
  return dataScopeType
    ? (DATA_SCOPE_LABELS[dataScopeType] ?? dataScopeType)
    : '-';
});

const topPermissions = computed(() =>
  (authStore.snapshot?.grants ?? []).slice(0, 8),
);

const loadStats = async () => {
  loading.value = true;
  try {
    const data = await getStats();
    stats.value = data;
    renderTrendChart();
  } finally {
    loading.value = false;
  }
};

const formatBytes = (bytes: number) => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / k ** i).toFixed(2)} ${sizes[i]}`;
};

const eventTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    LOGIN: '登录',
    LOGOUT: '登出',
    PASSWORD_CHANGE: '密码修改',
    ROLE_CHANGE: '角色变更',
    PERMISSION_CHANGE: '权限变更',
    DATA_EXPORT: '数据导出',
    SENSITIVE_OPERATION: '敏感操作',
  };
  return map[type] || type;
};

const healthTagType = (status: string) => {
  const map: Record<string, 'danger' | 'info' | 'success' | 'warning'> = {
    UP: 'success',
    DEGRADED: 'warning',
    DOWN: 'danger',
  };
  return map[status] || 'info';
};

const formatTimestamp = (timestamp?: null | string) =>
  formatRelativeTime(timestamp);

const renderTrendChart = () => {
  if (!chartRef.value || stats.value.dailyTrend.length === 0) {
    return;
  }

  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);
  }

  const dates = stats.value.dailyTrend.map((item) => item.date);
  const loginCounts = stats.value.dailyTrend.map((item) => item.loginCount);
  const operationCounts = stats.value.dailyTrend.map(
    (item) => item.operationCount,
  );
  const loginFailedCounts = stats.value.dailyTrend.map(
    (item) => item.loginFailedCount,
  );

  chartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
      },
    },
    legend: {
      data: ['登录成功', '操作日志', '登录失败'],
      top: 0,
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
    },
    yAxis: {
      type: 'value',
    },
    series: [
      {
        name: '登录成功',
        type: 'line',
        smooth: true,
        data: loginCounts,
        itemStyle: { color: '#67c23a' },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(103, 194, 58, 0.3)' },
              { offset: 1, color: 'rgba(103, 194, 58, 0.05)' },
            ],
          },
        },
      },
      {
        name: '操作日志',
        type: 'line',
        smooth: true,
        data: operationCounts,
        itemStyle: { color: '#409eff' },
      },
      {
        name: '登录失败',
        type: 'line',
        smooth: true,
        data: loginFailedCounts,
        itemStyle: { color: '#f56c6c' },
      },
    ],
  });
};

onMounted(() => {
  loadStats();

  // 响应窗口大小变化
  window.addEventListener('resize', () => {
    chartInstance?.resize();
  });
});
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div class="hx-table-toolbar" style="margin-bottom: 16px">
        <div>
          <h2>运行总览</h2>
        </div>
        <ElButton :icon="Refresh" @click="loadStats" :loading="loading">
          刷新
        </ElButton>
      </div>

      <ElRow :gutter="16">
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic title="用户总数" :value="stats.userCount" />
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic title="租户总数" :value="stats.tenantCount" />
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic title="角色总数" :value="stats.roleCount" />
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic title="文件总数" :value="stats.fileCount" />
          </ElCard>
        </ElCol>
      </ElRow>

      <ElRow :gutter="16" style="margin-top: 16px">
        <ElCol :span="6">
          <ElCard shadow="hover">
            <div class="metric-title">存储大小</div>
            <div class="metric-value">
              {{ formatBytes(stats.storageBytes) }}
            </div>
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic title="今日登录成功" :value="stats.todayLoginCount" />
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic title="当前在线" :value="stats.onlineUserCount" />
          </ElCard>
        </ElCol>
        <ElCol :span="6">
          <ElCard shadow="hover">
            <ElStatistic
              title="今日风险事件"
              :value="stats.todayRiskEventCount"
            >
              <template #suffix>
                <ElTag
                  v-if="stats.todayRiskEventCount > 0"
                  type="danger"
                  size="small"
                >
                  需关注
                </ElTag>
              </template>
            </ElStatistic>
          </ElCard>
        </ElCol>
      </ElRow>

      <ElRow :gutter="16" style="margin-top: 16px">
        <ElCol :span="16">
          <ElCard shadow="hover">
            <template #header>
              <span>近 7 天运行趋势</span>
            </template>
            <div ref="chartRef" style="width: 100%; height: 350px"></div>
          </ElCard>
        </ElCol>
        <ElCol :span="8">
          <ElCard shadow="hover">
            <template #header>
              <span>服务健康</span>
            </template>
            <div
              v-for="item in stats.serviceHealth"
              :key="item.code"
              style="margin-bottom: 8px"
            >
              <ElTag
                :type="healthTagType(item.status)"
                style="width: 80px; text-align: center"
              >
                {{ item.status }}
              </ElTag>
              <span style="margin-left: 8px"
                >{{ item.name }}：{{ item.message }}</span
              >
            </div>
          </ElCard>
        </ElCol>
      </ElRow>

      <ElRow :gutter="16" style="margin-top: 16px">
        <ElCol :span="24">
          <ElCard shadow="hover">
            <template #header>
              <span>最近审计事件</span>
            </template>
            <ElTable :data="stats.recentAuditEvents" style="width: 100%" stripe>
              <ElTableColumn label="事件类型" width="120">
                <template #default="{ row }">
                  <ElTag size="small" type="info">
                    {{ eventTypeLabel(row.eventType) }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn prop="operator" label="操作人" width="150" />
              <ElTableColumn prop="tenantId" label="租户ID" width="180" />
              <ElTableColumn prop="clientIp" label="客户端IP" width="150" />
              <ElTableColumn label="发生时间" width="150">
                <template #default="{ row }">
                  <span style="font-size: 13px; color: #909399">{{
                    formatTimestamp(row.occurredAt)
                  }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="精确时间">
                <template #default="{ row }">
                  <span style="font-size: 12px; color: #c0c4cc">
                    {{ formatDateTime(row.occurredAt) }}
                  </span>
                </template>
              </ElTableColumn>
            </ElTable>
            <div
              v-if="
                !stats.recentAuditEvents || stats.recentAuditEvents.length === 0
              "
              style="padding: 40px 0; color: #909399; text-align: center"
            >
              暂无审计事件
            </div>
          </ElCard>
        </ElCol>
      </ElRow>

      <!-- ── 权限快照面板 ── -->
      <ElRow :gutter="16" style="margin-top: 16px">
        <ElCol :span="24">
          <ElCard shadow="hover">
            <template #header>
              <span>当前权限快照</span>
            </template>
            <ElRow :gutter="16">
              <ElCol :span="6">
                <div class="stat-cell">
                  <span class="stat-eyebrow">当前用户</span>
                  <strong class="stat-value" style="font-size: 20px">{{
                    authStore.snapshot?.username || '-'
                  }}</strong>
                  <span class="stat-hint">{{
                    authStore.snapshot?.tenantId || '-'
                  }}</span>
                </div>
              </ElCol>
              <ElCol :span="6">
                <div class="stat-cell">
                  <span class="stat-eyebrow">数据权限</span>
                  <ElTag
                    size="small"
                    :type="
                      authStore.snapshot?.superAdmin ? 'danger' : 'primary'
                    "
                  >
                    {{ dataScopeLabel }}
                  </ElTag>
                  <span class="stat-hint">{{
                    authStore.snapshot?.superAdmin ? '超级管理员' : '按角色授权'
                  }}</span>
                </div>
              </ElCol>
              <ElCol :span="6">
                <div class="stat-cell">
                  <span class="stat-eyebrow">角色</span>
                  <div class="chip-list">
                    <ElTag
                      v-for="role in authStore.snapshot?.roles ?? []"
                      :key="role"
                      size="small"
                      effect="plain"
                      type="success"
                    >
                      {{ role }}
                    </ElTag>
                    <span
                      v-if="(authStore.snapshot?.roles ?? []).length === 0"
                      class="stat-hint"
                      >无</span
                    >
                  </div>
                </div>
              </ElCol>
              <ElCol :span="6">
                <div class="stat-cell">
                  <span class="stat-eyebrow">近期权限</span>
                  <div class="chip-list">
                    <ElTag
                      v-for="grant in topPermissions"
                      :key="grant"
                      size="small"
                      effect="plain"
                      class="mb-1"
                    >
                      {{ grant }}
                    </ElTag>
                    <span v-if="topPermissions.length === 0" class="stat-hint"
                      >无</span
                    >
                  </div>
                </div>
              </ElCol>
            </ElRow>
          </ElCard>
        </ElCol>
      </ElRow>
    </div>
  </div>
</template>

<style scoped>
.metric-title {
  font-size: 14px;
  color: #909399;
}

.metric-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

/* ── 权限快照面板 ── */
.stat-cell {
  display: grid;
  gap: 8px;
  text-align: center;
}

.stat-eyebrow {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-hint {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.mb-1 {
  margin-bottom: 4px;
}
</style>
