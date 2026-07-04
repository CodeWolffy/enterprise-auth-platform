<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ElButton, ElTabPane, ElTabs } from 'element-plus';

const router = useRouter();
const route = useRoute();
const activeTab = ref((route.name as string) || 'workflow-definitions');

watch(
  () => route.name,
  (name) => {
    if (typeof name === 'string') {
      activeTab.value = name;
    }
  },
);

function goRoute(name: string) {
  void router.push({ name });
}

function handleTabChange(name: number | string) {
  goRoute(String(name));
}
</script>

<template>
  <div class="workflow-shell">
    <section class="workflow-hero dashboard-panel">
      <div>
        <span class="eyebrow">Workflow</span>
        <h3>流程管理</h3>
        <p class="muted-line">
          覆盖流程定义、设计器、我的待办、我的已办和我的发起，入口已按当前项目路由体系接好。
        </p>
      </div>
      <div class="workflow-hero__actions">
        <ElButton @click="goRoute('workflow-definitions')">流程定义</ElButton>
        <ElButton type="primary" plain @click="goRoute('workflow-designer')">
          流程设计器
        </ElButton>
      </div>
    </section>

    <section class="workflow-nav">
      <ElTabs v-model="activeTab" @tab-change="handleTabChange">
        <ElTabPane label="流程定义" name="workflow-definitions" />
        <ElTabPane label="流程设计器" name="workflow-designer" />
        <ElTabPane label="我的待办" name="workflow-todo" />
        <ElTabPane label="我的已办" name="workflow-done" />
        <ElTabPane label="我的发起" name="workflow-instances" />
      </ElTabs>
    </section>

    <RouterView />
  </div>
</template>

<style scoped lang="scss">
.workflow-shell {
  display: grid;
  gap: 18px;
}

.workflow-hero {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 22px;
}

.workflow-hero h3 {
  margin: 8px 0 0;
  font-size: 24px;
}

.workflow-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.muted-line {
  margin: 8px 0 0;
  color: var(--text-soft);
}

.workflow-nav {
  padding: 0 4px;
}

@media (max-width: 860px) {
  .workflow-hero {
    display: grid;
  }
}
</style>
