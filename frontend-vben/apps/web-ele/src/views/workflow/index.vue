<template>
  <div class="workflow-shell">
    <section class="workflow-hero dashboard-panel">
      <div>
        <span class="eyebrow">Workflow</span>
        <h3>流程管理</h3>
        <p class="muted-line">覆盖流程定义、设计器、我的待办、我的已办和我的发起，入口已按当前项目路由体系接好。</p>
      </div>
      <div class="workflow-hero__actions">
        <el-button @click="goRoute('workflow-definitions')">流程定义</el-button>
        <el-button type="primary" plain @click="goRoute('workflow-designer')">流程设计器</el-button>
      </div>
    </section>

    <section class="workflow-nav">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="流程定义" name="workflow-definitions" />
        <el-tab-pane label="流程设计器" name="workflow-designer" />
        <el-tab-pane label="我的待办" name="workflow-todo" />
        <el-tab-pane label="我的已办" name="workflow-done" />
        <el-tab-pane label="我的发起" name="workflow-instances" />
      </el-tabs>
    </section>

    <RouterView />
  </div>
</template>

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

function handleTabChange(name: string | number) {
  goRoute(String(name));
}
</script>

<style scoped lang="scss">
.workflow-shell {
  display: grid;
  gap: 18px;
}

.workflow-hero {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  padding: 22px;
}

.workflow-hero h3 {
  margin: 8px 0 0;
  font-size: 24px;
}

.workflow-hero__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
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
