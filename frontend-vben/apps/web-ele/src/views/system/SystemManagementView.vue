<script setup lang="ts">
import type { FeatureFlags } from '#/types/api';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { queryFeatures } from '#/api/system';
import { getPage as getConfigPage } from '#/api/upms/config';
import { getPage as getDictPage } from '#/api/upms/dict';
import { getPage as getNoticePage } from '#/api/upms/notice';

const router = useRouter();

const dictCount = ref(0);
const configCount = ref(0);
const noticeCount = ref(0);
const publishedNoticeCount = ref(0);
const features = ref<FeatureFlags | null>(null);

const featureItems = computed(() => {
  if (!features.value) return [];
  return [
    {
      key: 'gatewayEnabled',
      label: 'Gateway',
      enabled: features.value.gatewayEnabled,
    },
    {
      key: 'nacosEnabled',
      label: 'Nacos',
      enabled: features.value.nacosEnabled,
    },
    { key: 'mqEnabled', label: 'RocketMQ', enabled: features.value.mqEnabled },
    {
      key: 'seataEnabled',
      label: 'Seata',
      enabled: features.value.seataEnabled,
    },
    { key: 'jobEnabled', label: 'XXL-Job', enabled: features.value.jobEnabled },
    { key: 'lokiEnabled', label: 'Loki', enabled: features.value.lokiEnabled },
  ];
});

onMounted(async () => {
  const [dicts, configs, notices, publishedNotices, featureData] =
    await Promise.all([
      getDictPage({ page: 1, size: 1 }),
      getConfigPage({ page: 1, size: 1 }),
      getNoticePage({ page: 1, size: 1 }),
      getNoticePage({ page: 1, size: 1, published: true }),
      queryFeatures(),
    ]);
  dictCount.value = dicts.total;
  configCount.value = configs.total;
  noticeCount.value = notices.total;
  publishedNoticeCount.value = publishedNotices.total;
  features.value = featureData;
});

const entryCards = [
  {
    title: '字典管理',
    eyebrow: '字典',
    desc: '维护业务枚举、状态映射和基础字典条目。',
    path: '/platform/dicts',
  },
  {
    title: '邮件配置',
    eyebrow: '邮件',
    desc: '配置 SMTP 服务器、授权管理和测试邮件发送。',
    path: '/platform/mail-channel',
  },
  {
    title: '参数管理',
    eyebrow: '参数',
    desc: '维护平台运行参数、业务配置项和策略型参数。',
    path: '/platform/configs',
  },
  {
    title: '公告管理',
    eyebrow: '公告',
    desc: '维护面向租户和运营人员的公告内容与发布时间。',
    path: '/platform/notices',
  },
  {
    title: '分类配置',
    eyebrow: '分类',
    desc: '维护字典分类和参数分类的匹配规则，并查看引用分析与趋势。',
    path: '/platform/categories',
  },
  {
    title: '租户套餐',
    eyebrow: '租户套餐',
    desc: '维护平台级租户套餐、能力目录，以及套餐与能力的绑定关系。',
    path: '/platform/tenant-catalog',
  },
  {
    title: '菜单管理',
    eyebrow: '菜单权限',
    desc: '维护目录、菜单、按钮和 API 权限节点，支持授权键、路由键、可见性与排序配置。',
    path: '/system/menus',
  },
];

function goTo(path: string) {
  router.push(path);
}
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 统计卡片 -->
      <el-row :gutter="16" class="mb-4">
        <el-col :span="6">
          <el-card shadow="never">
            <div class="stat-cell">
              <span class="stat-eyebrow">字典</span>
              <strong class="stat-value">{{ dictCount }}</strong>
              <span class="stat-hint">当前可见字典条目数量</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="stat-cell">
              <span class="stat-eyebrow">参数</span>
              <strong class="stat-value">{{ configCount }}</strong>
              <span class="stat-hint">当前可见参数条目数量</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="stat-cell">
              <span class="stat-eyebrow">公告</span>
              <strong class="stat-value">{{ noticeCount }}</strong>
              <span class="stat-hint">当前公告数量</span>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never">
            <div class="stat-cell">
              <span class="stat-eyebrow">已发布</span>
              <strong class="stat-value">{{ publishedNoticeCount }}</strong>
              <span class="stat-hint">已发布公告数量</span>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 功能入口卡片 -->
      <el-card shadow="never" class="mb-4">
        <div class="custom-card-header">
          <div>
            <span class="eyebrow">系统控制台</span>
            <h3 class="panel-title">系统管理工作台</h3>
          </div>
        </div>
        <el-row :gutter="16">
          <el-col
            v-for="card in entryCards"
            :key="card.path"
            :span="8"
            class="mb-3"
          >
            <div class="entry-card" @click="goTo(card.path)">
              <div>
                <span class="entry-eyebrow">{{ card.eyebrow }}</span>
                <h4 class="entry-title">{{ card.title }}</h4>
                <p class="entry-desc">{{ card.desc }}</p>
              </div>
              <el-button type="primary" size="small">进入</el-button>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 功能开关 -->
      <el-card shadow="never">
        <div class="custom-card-header">
          <div>
            <span class="eyebrow">功能开关</span>
            <h3 class="panel-title">预留组件状态</h3>
          </div>
        </div>
        <el-row :gutter="16">
          <el-col
            v-for="item in featureItems"
            :key="item.key"
            :span="8"
            class="mb-3"
          >
            <div class="feature-tag-row">
              <strong>{{ item.label }}</strong>
              <el-tag :type="item.enabled ? 'success' : 'info'">
                {{ item.enabled ? '已启用' : '预留' }}
              </el-tag>
            </div>
          </el-col>
        </el-row>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.custom-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.stat-cell {
  display: grid;
  gap: 8px;
  text-align: center;
}

.stat-eyebrow {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
  color: var(--el-text-color-primary);
}

.stat-hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.eyebrow {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.panel-title {
  margin: 4px 0 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.entry-card {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  cursor: pointer;
  border: 1px solid var(--el-border-color-light);
  border-radius: 18px;
  transition: all 0.2s ease;
}

.entry-card:hover {
  border-color: rgb(22 119 255 / 20%);
  box-shadow: 0 4px 12px rgb(0 0 0 / 4%);
  transform: translateY(-2px);
}

.entry-eyebrow {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
}

.entry-title {
  margin: 4px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.entry-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
}

.feature-tag-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  background: var(--el-fill-color-lighter);
  border-radius: 14px;
}

.mb-3 {
  margin-bottom: 12px;
}

.mb-4 {
  margin-bottom: 16px;
}
</style>
