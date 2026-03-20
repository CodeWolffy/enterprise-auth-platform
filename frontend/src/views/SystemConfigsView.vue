<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Configs</span>
        <strong>{{ filteredConfigs.length }}</strong>
        <span>当前筛选条件下的参数项总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Operators</span>
        <strong>{{ operatorCount }}</strong>
        <span>涉及的创建人数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Config</span>
          <h3>参数管理</h3>
        </div>
        <el-button type="primary" @click="openConfig()">新增参数</el-button>
      </div>

      <el-form :inline="true" class="toolbar-inline" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="搜索参数键、名称或值" clearable />
        </el-form-item>
      </el-form>

      <el-table :data="filteredConfigs" stripe>
        <el-table-column prop="configKey" label="参数键" min-width="180" />
        <el-table-column prop="configName" label="参数名称" min-width="180" />
        <el-table-column prop="configValue" label="参数值" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建人" min-width="120" />
        <el-table-column fixed="right" label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openConfig(row)">编辑</el-button>
            <el-button link type="danger" @click="removeConfig(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑参数' : '新增参数'" width="560px">
      <el-form label-position="top">
        <el-form-item label="参数键">
          <el-input v-model="form.configKey" />
        </el-form-item>
        <el-form-item label="参数名称">
          <el-input v-model="form.configName" />
        </el-form-item>
        <el-form-item label="参数值">
          <el-input v-model="form.configValue" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createConfig, deleteConfig, queryConfigs, updateConfig } from '@/api/system'
import type { ConfigView } from '@/types/auth'

const configs = ref<ConfigView[]>([])
const visible = ref(false)
const editingId = ref<number | null>(null)
const keyword = ref('')

const form = reactive({
  configKey: '',
  configName: '',
  configValue: '',
})

const filteredConfigs = computed(() =>
  configs.value.filter((item) => {
    const normalizedKeyword = keyword.value.trim().toLowerCase()
    return (
      !normalizedKeyword ||
      [item.configKey, item.configName, item.configValue].some((value) =>
        value.toLowerCase().includes(normalizedKeyword),
      )
    )
  }),
)

const operatorCount = computed(() => new Set(filteredConfigs.value.map((item) => item.createdBy)).size)

void load()

async function load() {
  configs.value = await queryConfigs()
}

function openConfig(row?: ConfigView) {
  editingId.value = row?.id ?? null
  Object.assign(form, row ?? { configKey: '', configName: '', configValue: '' })
  visible.value = true
}

async function submit() {
  if (editingId.value) {
    await updateConfig(editingId.value, form)
    ElMessage.success('参数已更新')
  } else {
    await createConfig(form)
    ElMessage.success('参数已创建')
  }
  visible.value = false
  await load()
}

async function removeConfig(id: number) {
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除确认', { type: 'warning' })
  await deleteConfig(id)
  ElMessage.success('参数已删除')
  await load()
}
</script>
