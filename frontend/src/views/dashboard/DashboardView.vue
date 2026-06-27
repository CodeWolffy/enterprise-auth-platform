<template>
  <div class="panel-stack dashboard-live">
    <section class="dashboard-grid dashboard-grid--animated">
      <article
        v-for="card in metricCards"
        :key="card.label"
        class="stat-card live-metric"
        :class="`live-metric--${card.tone}`"
      >
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.caption }}</small>
      </article>

      <section class="dashboard-panel dashboard-panel--wide dashboard-panel--trend" v-loading="loading">
        <div class="panel-head">
          <div>
            <span class="eyebrow">LIVE OPERATIONS</span>
            <h3>近 7 天运行趋势</h3>
            <p class="muted-line">
              登录、操作和失败事件按自然日汇总，统计口径随当前租户和数据权限变化。
            </p>
          </div>
          <el-button size="small" :loading="loading" @click="loadStats">刷新</el-button>
        </div>
        <div class="trend-shell">
          <div ref="chartRef" class="chart-host trend-chart"></div>
          <div class="trend-summary">
            <article>
              <span>统计作用域</span>
              <strong>{{ scopeText }}</strong>
              <small>{{ stats?.tenantId || authStore.snapshot?.tenantId || 'platform' }}</small>
            </article>
            <article>
              <span>账号规模</span>
              <strong>{{ formatNumber(stats?.userCount) }}</strong>
              <small>角色 {{ formatNumber(stats?.roleCount) }} · 租户 {{ formatNumber(stats?.tenantCount) }}</small>
            </article>
            <article>
              <span>文件资产</span>
              <strong>{{ formatNumber(stats?.fileCount) }}</strong>
              <small>{{ formatBytes(stats?.storageBytes || 0) }}</small>
            </article>
          </div>
        </div>
      </section>

      <section class="dashboard-panel dashboard-panel--health">
        <div class="panel-head">
          <div>
            <span class="eyebrow">HEALTH</span>
            <h3>系统健康</h3>
          </div>
          <span class="health-pill" :class="`health-pill--${healthOverall.toLowerCase()}`">
            {{ healthStatusText(healthOverall) }}
          </span>
        </div>
        <div class="health-stack">
          <article
            v-for="item in healthItems"
            :key="item.code"
            class="health-item"
            :class="`health-item--${item.status.toLowerCase()}`"
          >
            <i></i>
            <div>
              <strong>{{ item.name }}</strong>
              <span>{{ item.message }}</span>
            </div>
            <em>{{ healthStatusText(item.status) }}</em>
          </article>
        </div>
      </section>

      <section class="dashboard-panel dashboard-panel--events">
        <div class="panel-head">
          <div>
            <span class="eyebrow">AUDIT</span>
            <h3>最近审计事件</h3>
            <p class="muted-line">只展示最近 6 条，用于快速判断是否存在异常活动。</p>
          </div>
        </div>
        <div v-if="recentEvents.length" class="event-list">
          <article v-for="event in recentEvents" :key="`${event.eventType}-${event.occurredAt}-${event.operator}`">
            <div>
              <strong>{{ event.eventType }}</strong>
              <span>{{ event.operator || '-' }} · {{ event.tenantId || 'platform' }}</span>
            </div>
            <time>{{ formatDateTime(event.occurredAt) }}</time>
          </article>
        </div>
        <div v-else class="empty-state">
          暂无审计事件
        </div>
      </section>

      <section class="dashboard-panel dashboard-panel--snapshot">
        <div class="panel-head">
          <div>
            <span class="eyebrow">AUTHORIZATION</span>
            <h3>当前权限快照</h3>
          </div>
        </div>
        <div class="snapshot-stack">
          <article class="snapshot-block">
            <h4>当前用户</h4>
            <p>{{ authStore.snapshot?.username || '-' }}</p>
          </article>
          <article class="snapshot-block">
            <h4>数据权限</h4>
            <p>{{ authStore.snapshot?.dataScopeType || '-' }}</p>
          </article>
          <article class="snapshot-block">
            <h4>角色</h4>
            <div class="chip-list">
              <el-tag v-for="role in authStore.snapshot?.roles || []" :key="role" effect="plain" type="success">
                {{ role }}
              </el-tag>
            </div>
          </article>
          <article class="snapshot-block">
            <h4>近期权限</h4>
            <div class="chip-list">
              <el-tag v-for="permission in topPermissions" :key="permission" effect="plain">
                {{ permission }}
              </el-tag>
            </div>
          </article>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { init, use, type EChartsType } from 'echarts/core'
import { fetchDashboardStats, type DashboardStatsResponse } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime as formatEpochDateTime } from '@/utils/datetime'

use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const authStore = useAuthStore()
const chartRef = ref<HTMLDivElement | null>(null)
const stats = ref<DashboardStatsResponse | null>(null)
const loading = ref(false)
const topPermissions = computed(() => (authStore.snapshot?.grants ?? []).slice(0, 8))
const recentEvents = computed(() => stats.value?.recentAuditEvents ?? [])
const healthItems = computed(() => stats.value?.serviceHealth ?? [])
const healthOverall = computed(() => {
  const items = healthItems.value
  if (items.some((item) => item.status === 'DOWN')) {
    return 'DOWN'
  }
  if (items.some((item) => item.status === 'DEGRADED')) {
    return 'DEGRADED'
  }
  return 'UP'
})
const scopeText = computed(() => {
  if (stats.value?.scope === 'PLATFORM') {
    return '平台全局'
  }
  if (stats.value?.scope === 'VISIBLE') {
    return '可见范围'
  }
  return '租户范围'
})
const metricCards = computed(() => [
  {
    label: '今日登录',
    value: formatNumber(stats.value?.todayLoginCount),
    caption: '登录成功次数',
    tone: 'login',
  },
  {
    label: '当前在线',
    value: formatNumber(stats.value?.onlineUserCount),
    caption: '会话索引实时统计',
    tone: 'online',
  },
  {
    label: '今日操作',
    value: formatNumber(stats.value?.todayOperationLogCount),
    caption: '自然日操作日志',
    tone: 'operation',
  },
  {
    label: '失败 / 风险',
    value: `${formatNumber(stats.value?.todayLoginFailedCount)} / ${formatNumber(stats.value?.todayRiskEventCount)}`,
    caption: '登录失败与锁定事件',
    tone: 'risk',
  },
])

let chart: EChartsType | null = null

async function loadStats() {
  loading.value = true
  try {
    stats.value = await fetchDashboardStats()
    await renderChart()
  } finally {
    loading.value = false
  }
}

async function renderChart() {
  await nextTick()
  if (!chartRef.value) {
    return
  }
  if (!chart) {
    chart = init(chartRef.value)
  }
  const trend = stats.value?.dailyTrend ?? []
  chart.setOption({
    backgroundColor: 'transparent',
    color: ['#1677ff', '#14b8a6', '#f59e0b'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff' },
    },
    legend: {
      top: 0,
      right: 8,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#64748b' },
    },
    grid: {
      left: 8,
      right: 16,
      top: 42,
      bottom: 8,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trend.map((item) => item.date.slice(5)),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#dbe3ef' } },
      axisLabel: { color: '#64748b' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.18)' } },
    },
    series: [
      {
        name: '登录',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.08 },
        data: trend.map((item) => item.loginCount),
      },
      {
        name: '操作',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.08 },
        data: trend.map((item) => item.operationCount),
      },
      {
        name: '失败',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        lineStyle: { width: 3 },
        areaStyle: { opacity: 0.08 },
        data: trend.map((item) => item.loginFailedCount),
      },
    ],
  })
}

function resizeChart() {
  chart?.resize()
}

function formatNumber(value?: number | null) {
  return (value ?? 0).toLocaleString()
}

function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  }
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function healthStatusText(status: string) {
  if (status === 'UP') {
    return '正常'
  }
  if (status === 'DEGRADED') {
    return '降级'
  }
  if (status === 'DOWN') {
    return '异常'
  }
  return status
}

function formatDateTime(epochMs?: number | null) {
  return formatEpochDateTime(epochMs)
}

onMounted(() => {
  void loadStats()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
  chart = null
})

watch(() => authStore.tenantId, () => {
  void loadStats()
})
</script>

<style scoped lang="scss">
.live-metric {
  position: relative;
  overflow: hidden;
  min-height: 132px;
  border: 1px solid rgba(148, 163, 184, 0.18);

  &::after {
    content: '';
    position: absolute;
    right: -32px;
    bottom: -42px;
    width: 118px;
    height: 118px;
    border-radius: 999px;
    opacity: 0.16;
    background: var(--metric-accent);
  }

  span,
  small,
  strong {
    position: relative;
    z-index: 1;
  }

  strong {
    font-size: clamp(28px, 3vw, 40px);
  }
}

.live-metric--login {
  --metric-accent: #1677ff;
}

.live-metric--online {
  --metric-accent: #14b8a6;
}

.live-metric--operation {
  --metric-accent: #f59e0b;
}

.live-metric--risk {
  --metric-accent: #ef4444;
}

.dashboard-panel--trend {
  min-height: 430px;
}

.trend-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 18px;
  align-items: stretch;
  padding-top: 4px;
}

.trend-chart {
  min-height: 330px;
}

.trend-summary {
  display: grid;
  gap: 12px;

  article {
    display: grid;
    gap: 6px;
    padding: 14px;
    border: 1px solid var(--line);
    border-radius: 14px;
    background:
      linear-gradient(145deg, rgba(22, 119, 255, 0.08), transparent 54%),
      var(--bg-card-muted);
  }

  span,
  small {
    color: var(--text-soft);
    font-size: 12px;
  }

  strong {
    font-size: 22px;
    line-height: 1.1;
  }
}

.muted-line {
  margin: 8px 0 0;
  color: var(--text-soft);
}

.health-pill {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: rgba(22, 199, 132, 0.12);
  color: #0f9f69;
}

.health-pill--degraded {
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.health-pill--down {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
}

.health-stack,
.event-list,
.snapshot-stack {
  display: grid;
  gap: 12px;
}

.health-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 13px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--bg-card-muted);

  i {
    width: 9px;
    height: 9px;
    border-radius: 999px;
    background: #16c784;
    box-shadow: 0 0 0 5px rgba(22, 199, 132, 0.12);
  }

  div {
    display: grid;
    gap: 4px;
  }

  strong {
    font-size: 14px;
  }

  span {
    color: var(--text-soft);
    font-size: 12px;
  }

  em {
    color: var(--text-soft);
    font-size: 12px;
    font-style: normal;
    font-weight: 700;
  }
}

.health-item--degraded i {
  background: #f59e0b;
  box-shadow: 0 0 0 5px rgba(245, 158, 11, 0.14);
}

.health-item--down i {
  background: #ef4444;
  box-shadow: 0 0 0 5px rgba(239, 68, 68, 0.12);
}

.dashboard-panel--events,
.dashboard-panel--snapshot {
  grid-column: span 2;
}

.event-list article {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 0;
  border-bottom: 1px solid var(--line);

  &:last-child {
    border-bottom: 0;
  }

  div {
    display: grid;
    gap: 5px;
  }

  strong {
    font-size: 14px;
  }

  span,
  time {
    color: var(--text-soft);
    font-size: 12px;
  }

  time {
    white-space: nowrap;
  }
}

.empty-state {
  display: grid;
  min-height: 180px;
  place-items: center;
  border: 1px dashed var(--line);
  border-radius: 14px;
  color: var(--text-soft);
  background: var(--bg-card-muted);
}

.snapshot-block {
  display: grid;
  gap: 10px;

  h4 {
    margin: 0;
    font-size: 13px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: var(--text-soft);
  }

  p {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
  }
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 1320px) {
  .trend-shell {
    grid-template-columns: 1fr;
  }

  .trend-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .dashboard-panel--events,
  .dashboard-panel--snapshot {
    grid-column: span 4;
  }
}

@media (max-width: 860px) {
  .trend-summary {
    grid-template-columns: 1fr;
  }

  .event-list article {
    display: grid;
  }
}
</style>
