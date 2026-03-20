<template>
  <div class="dashboard-grid">
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
      <strong>{{ authStore.snapshot?.permissions.length ?? 0 }}</strong>
      <small>接口与菜单权限已生效</small>
    </article>
    <article class="stat-card">
      <span>菜单数量</span>
      <strong>{{ authStore.snapshot?.menus.length ?? 0 }}</strong>
      <small>前端菜单来自权限快照</small>
    </article>

    <section class="dashboard-panel dashboard-panel--wide">
      <div class="panel-head">
        <div>
          <span class="eyebrow">ECharts</span>
          <h3>权限结构概览</h3>
        </div>
      </div>
      <div ref="chartRef" class="chart-host"></div>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Authorization</span>
          <h3>当前权限快照</h3>
        </div>
      </div>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="数据权限">{{ authStore.snapshot?.dataScopeType }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          {{ authStore.snapshot?.roles.join(', ') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="菜单">
          {{ authStore.snapshot?.menus.map((item) => item.title).join(' / ') || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </section>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { PieChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { init, use, type EChartsType } from 'echarts/core'
import { useAuthStore } from '@/stores/auth'

use([PieChart, TooltipComponent, CanvasRenderer])

const authStore = useAuthStore()
const chartRef = ref<HTMLDivElement | null>(null)
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
          { name: '权限', value: authStore.snapshot.permissions.length, itemStyle: { color: '#c96b29' } },
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
