<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Dicts</span>
        <strong>{{ dictCount }}</strong>
        <span>当前可见字典条目数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Configs</span>
        <strong>{{ configCount }}</strong>
        <span>当前可见参数条目数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Notices</span>
        <strong>{{ noticeCount }}</strong>
        <span>当前公告数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Published</span>
        <strong>{{ publishedNoticeCount }}</strong>
        <span>已发布公告数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">System Console</span>
          <h3>系统管理工作台</h3>
        </div>
      </div>

      <div class="setting-grid">
        <article class="setting-card">
          <div>
            <span class="eyebrow">Dictionary</span>
            <h4>字典管理</h4>
            <p>维护业务枚举、状态映射和基础字典条目。</p>
          </div>
          <el-button type="primary" @click="goTo('settings-dicts')">进入字典管理</el-button>
        </article>

        <article class="setting-card">
          <div>
            <span class="eyebrow">Config</span>
            <h4>参数管理</h4>
            <p>维护平台运行参数、业务配置项和策略型参数。</p>
          </div>
          <el-button type="primary" @click="goTo('settings-configs')">进入参数管理</el-button>
        </article>

        <article class="setting-card">
          <div>
            <span class="eyebrow">Notice</span>
            <h4>公告管理</h4>
            <p>维护面向租户和运营人员的公告内容与发布时间。</p>
          </div>
          <el-button type="primary" @click="goTo('settings-notices')">进入公告管理</el-button>
        </article>

        <article class="setting-card">
          <div>
            <span class="eyebrow">Category</span>
            <h4>分类配置</h4>
            <p>维护字典分类和参数分类的匹配规则，并查看引用分析与趋势。</p>
          </div>
          <el-button type="primary" @click="goTo('settings-categories')">进入分类配置</el-button>
        </article>

        <article class="setting-card">
          <div>
            <span class="eyebrow">Tenant Catalog</span>
            <h4>套餐与能力</h4>
            <p>维护平台级租户套餐、能力目录，以及套餐与能力的绑定关系。</p>
          </div>
          <el-button type="primary" @click="goTo('tenant-catalog')">进入套餐与能力</el-button>
        </article>

        <article class="setting-card">
          <div>
            <span class="eyebrow">Resource Auth</span>
            <h4>菜单管理</h4>
            <p>维护统一资源树（目录/菜单/按钮/API），支持授权键、路由键、可见性与排序配置。</p>
          </div>
          <el-button type="primary" @click="goTo('settings-resources')">进入菜单管理</el-button>
        </article>
      </div>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Feature Flags</span>
          <h3>预留组件状态</h3>
        </div>
      </div>

      <el-row :gutter="16" class="feature-grid">
        <el-col v-for="item in featureItems" :key="item.key" :span="8">
          <div class="feature-card">
            <strong>{{ item.label }}</strong>
            <el-tag :type="item.enabled ? 'success' : 'info'">{{ item.enabled ? '已启用' : '预留' }}</el-tag>
          </div>
        </el-col>
      </el-row>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { queryConfigs, queryDicts, queryFeatures, queryNotices } from '@/api/system'
import type { FeatureFlags } from '@/types/auth'

const router = useRouter()
const dictCount = ref(0)
const configCount = ref(0)
const noticeCount = ref(0)
const publishedNoticeCount = ref(0)
const features = ref<FeatureFlags | null>(null)

const featureItems = computed(() => {
  if (!features.value) {
    return []
  }
  return [
    { key: 'gatewayEnabled', label: 'Gateway', enabled: features.value.gatewayEnabled },
    { key: 'nacosEnabled', label: 'Nacos', enabled: features.value.nacosEnabled },
    { key: 'mqEnabled', label: 'RocketMQ', enabled: features.value.mqEnabled },
    { key: 'seataEnabled', label: 'Seata', enabled: features.value.seataEnabled },
    { key: 'jobEnabled', label: 'XXL-Job', enabled: features.value.jobEnabled },
    { key: 'lokiEnabled', label: 'Loki', enabled: features.value.lokiEnabled },
  ]
})

void load()

async function load() {
  const [dicts, configs, notices, publishedNotices, featureData] = await Promise.all([
    queryDicts({ page: 1, size: 1 }),
    queryConfigs({ page: 1, size: 1 }),
    queryNotices({ page: 1, size: 1 }),
    queryNotices({ page: 1, size: 1, published: true }),
    queryFeatures(),
  ])
  dictCount.value = dicts.total
  configCount.value = configs.total
  noticeCount.value = notices.total
  publishedNoticeCount.value = publishedNotices.total
  features.value = featureData
}

function goTo(
  name: 'settings-dicts' | 'settings-configs' | 'settings-notices' | 'settings-categories' | 'tenant-catalog' | 'settings-resources',
) {
  void router.push({ name })
}
</script>

<style scoped lang="scss">
.setting-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.setting-card {
  display: grid;
  gap: 16px;
  padding: 20px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 20px;
  background: rgba(248, 250, 252, 0.88);

  h4 {
    margin: 8px 0;
    font-size: 18px;
  }

  p {
    margin: 0;
    color: #475569;
    line-height: 1.6;
  }
}
</style>
