<script setup lang="ts">
import type { FeatureFlags } from '#/types/api';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElCol,
  ElEmpty,
  ElRow,
  ElTag,
} from 'element-plus';

import { queryFeatures } from '#/api/system';
import { getPage as getConfigPage } from '#/api/upms/config';
import { getPage as getDictPage } from '#/api/upms/dict';
import { getPage as getNoticePage } from '#/api/upms/notice';
import { PERMS } from '#/constants/permissions';

const router = useRouter();
const { hasAccessByCodes } = useAccess();

const loading = ref(false);
const loadErrors = ref<string[]>([]);
const dictCount = ref<null | number>(null);
const configCount = ref<null | number>(null);
const noticeCount = ref<null | number>(null);
const publishedNoticeCount = ref<null | number>(null);
const features = ref<FeatureFlags | null>(null);

function canAccess(code: string) {
  return hasAccessByCodes([code]);
}

const canLoadFeatures = computed(() => canAccess(PERMS.upms.system.get));

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

const entryCards = [
  {
    title: '字典管理',
    eyebrow: '字典',
    desc: '维护业务枚举、状态映射和基础字典条目。',
    path: '/platform/dicts',
    permission: PERMS.upms.dict.page,
  },
  {
    title: '邮件配置',
    eyebrow: '邮件',
    desc: '配置 SMTP 服务器、授权管理和测试邮件发送。',
    path: '/platform/mail-channel',
    permission: PERMS.upms.mail.page,
  },
  {
    title: '参数管理',
    eyebrow: '参数',
    desc: '维护平台运行参数、业务配置项和策略型参数。',
    path: '/platform/configs',
    permission: PERMS.upms.config.page,
  },
  {
    title: '公告管理',
    eyebrow: '公告',
    desc: '维护面向租户和运营人员的公告内容与发布时间。',
    path: '/platform/notices',
    permission: PERMS.upms.notice.page,
  },
  {
    title: '分类配置',
    eyebrow: '分类',
    desc: '维护字典分类和参数分类的匹配规则，并查看引用分析与趋势。',
    path: '/platform/categories',
    permission: PERMS.upms.category.page,
  },
  {
    title: '租户套餐',
    eyebrow: '租户套餐',
    desc: '维护平台级租户套餐、能力目录，以及套餐与能力的绑定关系。',
    path: '/platform/tenant-catalog',
    permission: PERMS.upms.tenantPackage.page,
  },
  {
    title: '菜单管理',
    eyebrow: '菜单权限',
    desc: '维护目录、菜单、按钮和 API 权限节点，支持授权键、路由键、可见性与排序配置。',
    path: '/system/menus',
    permission: PERMS.upms.menu.page,
  },
];

const visibleEntryCards = computed(() =>
  entryCards.filter((card) => canAccess(card.permission)),
);

const statCards = computed(() =>
  [
    {
      count: dictCount.value,
      eyebrow: '字典',
      hint: '当前可见字典条目数量',
      permission: PERMS.upms.dict.page,
    },
    {
      count: configCount.value,
      eyebrow: '参数',
      hint: '当前可见参数条目数量',
      permission: PERMS.upms.config.page,
    },
    {
      count: noticeCount.value,
      eyebrow: '公告',
      hint: '当前公告数量',
      permission: PERMS.upms.notice.page,
    },
    {
      count: publishedNoticeCount.value,
      eyebrow: '已发布',
      hint: '已发布公告数量',
      permission: PERMS.upms.notice.page,
    },
  ].filter((card) => canAccess(card.permission)),
);

onMounted(async () => {
  loading.value = true;
  loadErrors.value = [];

  const tasks: Array<{ label: string; request: Promise<void> }> = [];
  if (canAccess(PERMS.upms.dict.page)) {
    tasks.push({
      label: '字典统计',
      request: getDictPage({ page: 1, size: 1 }).then((data) => {
        dictCount.value = data.total ?? 0;
      }),
    });
  }
  if (canAccess(PERMS.upms.config.page)) {
    tasks.push({
      label: '参数统计',
      request: getConfigPage({ page: 1, size: 1 }).then((data) => {
        configCount.value = data.total ?? 0;
      }),
    });
  }
  if (canAccess(PERMS.upms.notice.page)) {
    tasks.push(
      {
        label: '公告统计',
        request: getNoticePage({ page: 1, size: 1 }).then((data) => {
          noticeCount.value = data.total ?? 0;
        }),
      },
      {
        label: '已发布公告统计',
        request: getNoticePage({ page: 1, published: true, size: 1 }).then(
          (data) => {
            publishedNoticeCount.value = data.total ?? 0;
          },
        ),
      },
    );
  }
  if (canLoadFeatures.value) {
    tasks.push({
      label: '系统特性',
      request: queryFeatures().then((data) => {
        features.value = data;
      }),
    });
  }

  const results = await Promise.allSettled(tasks.map((task) => task.request));
  loadErrors.value = results.flatMap((result, index) =>
    result.status === 'rejected' ? [tasks[index]?.label ?? '未知模块'] : [],
  );
  loading.value = false;
});

function goTo(path: string) {
  router.push(path);
}
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 统计卡片 -->
      <ElAlert
        v-if="loadErrors.length > 0"
        :closable="false"
        class="mb-4"
        show-icon
        title="部分模块加载失败"
        :description="`${loadErrors.join('、')}暂时不可用，其他模块不受影响。`"
        type="warning"
      />

      <ElRow
        v-if="statCards.length > 0"
        v-loading="loading"
        :gutter="16"
        class="mb-4"
      >
        <ElCol
          v-for="card in statCards"
          :key="card.eyebrow"
          :lg="6"
          :md="12"
          :sm="12"
          :xs="24"
        >
          <ElCard shadow="never">
            <div class="stat-cell">
              <span class="stat-eyebrow">{{ card.eyebrow }}</span>
              <strong class="stat-value">{{ card.count ?? '-' }}</strong>
              <span class="stat-hint">{{ card.hint }}</span>
            </div>
          </ElCard>
        </ElCol>
      </ElRow>

      <!-- 功能入口卡片 -->
      <ElCard shadow="never" class="mb-4">
        <div class="custom-card-header">
          <div>
            <span class="eyebrow">系统控制台</span>
            <h3 class="panel-title">系统管理工作台</h3>
          </div>
        </div>
        <ElRow :gutter="16">
          <ElCol
            v-for="card in visibleEntryCards"
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
              <ElButton type="primary" size="small">进入</ElButton>
            </div>
          </ElCol>
        </ElRow>
        <ElEmpty
          v-if="visibleEntryCards.length === 0"
          description="暂无可访问的系统管理模块"
        />
      </ElCard>

      <!-- 功能开关 -->
      <ElCard v-if="canLoadFeatures" v-loading="loading" shadow="never">
        <div class="custom-card-header">
          <div>
            <span class="eyebrow">功能开关</span>
            <h3 class="panel-title">预留组件状态</h3>
          </div>
        </div>
        <ElRow :gutter="16">
          <ElCol
            v-for="item in featureItems"
            :key="item.key"
            :span="8"
            class="mb-3"
          >
            <div class="feature-tag-row">
              <strong>{{ item.label }}</strong>
              <ElTag :type="item.enabled ? 'success' : 'info'">
                {{ item.enabled ? '已启用' : '预留' }}
              </ElTag>
            </div>
          </ElCol>
        </ElRow>
      </ElCard>
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
