<template>
  <div class="panel-stack">
    <section class="dashboard-grid dashboard-grid--animated">
      <article class="stat-card">
        <span>当前用户</span>
        <strong>{{ authStore.snapshot?.username }}</strong>
        <small>{{ authStore.snapshot?.tenantId }}</small>
      </article>
      <article class="stat-card">
        <span>角色数量</span>
        <strong>{{ authStore.snapshot?.roles.length ?? 0 }}</strong>
        <small>RBAC 已加载</small>
      </article>
      <article class="stat-card">
        <span>权限数量</span>
        <strong>{{ authStore.snapshot?.grants.length ?? 0 }}</strong>
        <small>接口与菜单权限已生效</small>
      </article>
      <article class="stat-card">
        <span>菜单数量</span>
        <strong>{{ authStore.snapshot?.menus.length ?? 0 }}</strong>
        <small>来自权限快照</small>
      </article>

      <section class="dashboard-panel dashboard-panel--wide dashboard-panel--chart">
        <div class="panel-head">
          <div>
            <span class="eyebrow">ECharts</span>
            <h3>权限结构概览</h3>
          </div>
        </div>
        <div class="dashboard-panel__body">
          <div ref="chartRef" class="chart-host"></div>
          <aside class="insight-stack">
            <article class="insight-card">
              <span>角色</span>
              <strong>{{ authStore.snapshot?.roles.length ?? 0 }}</strong>
            </article>
            <article class="insight-card">
              <span>权限</span>
              <strong>{{ authStore.snapshot?.grants.length ?? 0 }}</strong>
            </article>
            <article class="insight-card">
              <span>菜单</span>
              <strong>{{ authStore.snapshot?.menus.length ?? 0 }}</strong>
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
import { useAuthStore } from '@/stores/auth'

use([PieChart, TooltipComponent, CanvasRenderer])

const authStore = useAuthStore()
const chartRef = ref<HTMLDivElement | null>(null)
const topMenus = computed(() => (authStore.snapshot?.menus ?? []).map((item) => item.title).slice(0, 6))
const topPermissions = computed(() => (authStore.snapshot?.grants ?? []).slice(0, 8))

let chart: EChartsType | null = null

async function renderChart() {
  await nextTick()
  if (!chartRef.value || !authStore.snapshot) {
    return
  }
  if (!chart) {
    chart = init(chartRef.value)
  }
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['48%', '72%'],
        itemStyle: {
          borderRadius: 18,
          borderColor: '#fff8ef',
          borderWidth: 6,
        },
        label: {
          color: '#2f2a24',
          formatter: '{b}\n{c}',
        },
        data: [
          { name: '角色', value: authStore.snapshot.roles.length, itemStyle: { color: '#0f766e' } },
          { name: '权限', value: authStore.snapshot.grants.length, itemStyle: { color: '#c96b29' } },
          { name: '菜单', value: authStore.snapshot.menus.length, itemStyle: { color: '#355c7d' } },
        ],
      },
    ],
  })
}

function resizeChart() {
  chart?.resize()
}

onMounted(() => {
  void renderChart()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
  chart = null
})

watch(() => authStore.snapshot, () => {
  void renderChart()
})
</script>

<style scoped lang="scss">
.dashboard-panel__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
  align-items: stretch;
}

.insight-stack {
  display: grid;
  grid-template-rows: repeat(3, minmax(72px, auto)) 1fr;
  gap: 10px;
}

.insight-card {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(57, 44, 28, 0.1);
  background: rgba(255, 255, 255, 0.6);

  span {
    color: #6b5f51;
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
  border: 1px solid rgba(57, 44, 28, 0.1);
  background: rgba(255, 255, 255, 0.5);
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
  padding: 12px;
  border-radius: 12px;
  border: 1px solid rgba(57, 44, 28, 0.1);
  background: rgba(255, 255, 255, 0.55);
  display: grid;
  gap: 10px;

  h4 {
    margin: 0;
    font-size: 13px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #6b5f51;
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
