<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ElButton, ElTabPane, ElTabs } from 'element-plus';

const router = useRouter();
const route = useRoute();
const activeTab = ref((route.name as string) || 'workflow-definitions');
const workflowStages = [
  {
    label: '定义',
    title: '建模部署',
    text: '草稿、节点、候选人与驳回策略',
  },
  {
    label: '发起',
    title: '实例流转',
    text: '业务键、变量快照、撤回与终止',
  },
  {
    label: '审批',
    title: '待办处理',
    text: '通过、驳回、转签、催办提醒',
  },
  {
    label: '沉淀',
    title: '记录追踪',
    text: '已办、催办历史和实例详情',
  },
];

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
      <div class="workflow-hero__copy">
        <span class="eyebrow">Workflow Center</span>
        <h3>流程管理工作台</h3>
        <p class="muted-line">
          从流程建模、发起审批到催办留痕，串起企业后台里的轻量审批闭环。
        </p>
        <div class="workflow-hero__chips" aria-label="工作流能力">
          <span>多租户隔离</span>
          <span>RBAC 候选人</span>
          <span>变量快照</span>
          <span>审计留痕</span>
        </div>
      </div>
      <div class="workflow-hero__side">
        <div class="workflow-flow-map">
          <article
            v-for="(stage, index) in workflowStages"
            :key="stage.label"
            class="workflow-flow-node"
          >
            <span>{{ index + 1 }}</span>
            <div>
              <strong>{{ stage.title }}</strong>
              <small>{{ stage.text }}</small>
            </div>
          </article>
        </div>
        <div class="workflow-hero__actions">
          <ElButton @click="goRoute('workflow-definitions')">流程定义</ElButton>
          <ElButton type="primary" plain @click="goRoute('workflow-designer')">
            流程设计器
          </ElButton>
        </div>
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
  display: grid;
  grid-template-columns: minmax(320px, 0.9fr) minmax(360px, 1.1fr);
  gap: 24px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 26px;
  background:
    linear-gradient(135deg, rgb(22 119 255 / 12%), rgb(20 184 166 / 8%)),
    var(--bg-card);
}

.workflow-hero h3 {
  margin: 8px 0 0;
  font-size: 28px;
  line-height: 1.2;
}

.workflow-hero__copy {
  display: grid;
  gap: 10px;
  align-content: start;
  min-width: 0;
}

.workflow-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;

  span {
    padding: 6px 10px;
    font-size: 12px;
    font-weight: 700;
    color: var(--accent);
    background: var(--accent-soft);
    border: 1px solid rgb(22 119 255 / 14%);
    border-radius: 999px;
  }
}

.workflow-hero__side {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.workflow-flow-map {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.workflow-flow-node {
  position: relative;
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  background: rgb(255 255 255 / 72%);
  border: 1px solid rgb(22 119 255 / 12%);
  border-radius: 12px;
  box-shadow: 0 12px 28px rgb(15 23 42 / 5%);

  > span {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    font-size: 13px;
    font-weight: 800;
    color: #fff;
    background: var(--accent);
    border-radius: 999px;
  }

  strong,
  small {
    display: block;
  }

  strong {
    font-size: 14px;
    color: var(--text-main);
  }

  small {
    margin-top: 4px;
    line-height: 1.5;
    color: var(--text-soft);
  }
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
  line-height: 1.7;
}

.workflow-nav {
  padding: 0 4px;
}

@media (max-width: 860px) {
  .workflow-hero {
    display: grid;
    grid-template-columns: 1fr;
  }

  .workflow-flow-map {
    grid-template-columns: 1fr;
  }
}
</style>
