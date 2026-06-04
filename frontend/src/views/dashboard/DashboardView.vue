<template>
  <div class="panel-stack dashboard-live">
    <section class="dashboard-grid dashboard-grid--animated">
      <article class="stat-card live-card live-card--identity">
        <span>当前用户</span>
        <strong>{{ authStore.snapshot?.username }}</strong>
        <small>{{ scopeText }} · {{ stats?.tenantId || authStore.snapshot?.tenantId || 'platform' }}</small>
      </article>
      <article class="stat-card">
        <span>用户数量</span>
        <strong>{{ formatNumber(stats?.userCount) }}</strong>
        <small>当前作用域内账号</small>
      </article>
      <article class="stat-card">
        <span>文件数量</span>
        <strong>{{ formatNumber(stats?.fileCount) }}</strong>
        <small>{{ formatBytes(stats?.storageBytes || 0) }}</small>
      </article>
      <article class="stat-card">
        <span>24h 操作</span>
        <strong>{{ formatNumber(stats?.recentOperationLogCount) }}</strong>
        <small>操作日志近期增量</small>
      </article>

      <section class="dashboard-panel dashboard-panel--wide dashboard-panel--chart" v-loading="loading">
        <div class="panel-head">
          <div>
            <span class="eyebrow">ECharts</span>
            <h3>平台数据结构</h3>
            <p class="muted-line">统计口径随当前租户和数据权限变化。</p>
          </div>
          <el-button size="small" :loading="loading" @click="loadStats">刷新</el-button>
        </div>
        <div class="dashboard-panel__body">
          <div ref="chartRef" class="chart-host"></div>
          <aside class="insight-stack">
            <article class="insight-card">
              <span>角色</span>
              <strong>{{ formatNumber(stats?.roleCount) }}</strong>
            </article>
            <article class="insight-card">
              <span>租户</span>
              <strong>{{ formatNumber(stats?.tenantCount) }}</strong>
            </article>
            <article class="insight-card">
              <span>操作日志</span>
              <strong>{{ formatNumber(stats?.operationLogCount) }}</strong>
            </article>

            <div class="insight-tags">
              <span class="eyebrow">高频菜单</span>
              <div class="tag-list">
                <el-tag v-for="menu in topMenus" :key="menu" effect="plain" type="info">
                  {{ menu }}
                </el-tag>
              </div>
            </div>
          </aside>
        </div>
      </section>

      <section class="dashboard-panel dashboard-panel--snapshot">
        <div class="panel-head">
          <div>
            <span class="eyebrow">授权</span>
            <h3>当前权限快照</h3>
          </div>
        </div>
        <div class="snapshot-stack">
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
import { PieChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { init, use, type EChartsType } from 'echarts/core'
import { fetchDashboardStats, type DashboardStatsResponse } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'

use([PieChart, TooltipComponent, CanvasRenderer])

const authStore = useAuthStore()
const chartRef = ref<HTMLDivElement | null>(null)
const stats = ref<DashboardStatsResponse | null>(null)
const loading = ref(false)
const topMenus = computed(() => (authStore.snapshot?.menus ?? []).map((item) => item.title).slice(0, 6))
const topPermissions = computed(() => (authStore.snapshot?.grants ?? []).slice(0, 8))
const scopeText = computed(() => {
  if (stats.value?.scope === 'PLATFORM') {
    return '平台全局'
  }
  if (stats.value?.scope === 'VISIBLE') {
    return '可见范围'
  }
  return '租户范围'
})

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
  const current = stats.value
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['48%', '72%'],
        itemStyle: {
          borderRadius: 18,
          borderColor: '#ffffff',
          borderWidth: 6,
        },
        label: {
          color: '#1f2937',
          formatter: '{b}\n{c}',
        },
        data: [
          { name: '用户', value: current?.userCount ?? 0, itemStyle: { color: '#1677ff' } },
          { name: '角色', value: current?.roleCount ?? 0, itemStyle: { color: '#14b8a6' } },
          { name: '文件', value: current?.fileCount ?? 0, itemStyle: { color: '#16c784' } },
          { name: '日志', value: current?.operationLogCount ?? 0, itemStyle: { color: '#f59e0b' } },
        ],
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
.dashboard-panel__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
  align-items: stretch;
}

.live-card--identity {
  background:
    linear-gradient(135deg, rgba(22, 119, 255, 0.14), rgba(20, 184, 166, 0.1)),
    var(--bg-card);
}

.muted-line {
  margin: 8px 0 0;
  color: var(--text-soft);
}

.insight-stack {
  display: grid;
  grid-template-rows: repeat(3, minmax(72px, auto)) 1fr;
  gap: 10px;
}

.insight-card {
  display: grid;
  gap: 4px;

  span {
    color: var(--text-soft);
    font-size: 12px;
  }

  strong {
    font-size: 28px;
    line-height: 1;
  }
}

.insight-tags {
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--line);
  background: var(--bg-card-muted);
  display: grid;
  gap: 10px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.snapshot-stack {
  display: grid;
  gap: 12px;
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
  .dashboard-panel__body {
    grid-template-columns: 1fr;
  }

  .insight-stack {
    grid-template-rows: unset;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .insight-tags {
    grid-column: span 3;
  }
}

@media (max-width: 860px) {
  .insight-stack {
    grid-template-columns: 1fr;
  }

  .insight-tags {
    grid-column: span 1;
  }
}
</style>