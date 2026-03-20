<template>
  <div class="panel-stack">
    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">System Console</span>
          <h3>系统管理联调</h3>
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

      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="字典" name="dict">
          <div class="toolbar-inline">
            <el-button type="primary" @click="openDict()">新增字典</el-button>
          </div>
          <el-table :data="dicts" stripe>
            <el-table-column prop="dictType" label="类型" />
            <el-table-column prop="dictCode" label="编码" />
            <el-table-column prop="dictValue" label="值" />
            <el-table-column prop="createdBy" label="创建人" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDict(row)">编辑</el-button>
                <el-button link type="danger" @click="removeDict(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="参数" name="config">
          <div class="toolbar-inline">
            <el-button type="primary" @click="openConfig()">新增参数</el-button>
          </div>
          <el-table :data="configs" stripe>
            <el-table-column prop="configKey" label="键" />
            <el-table-column prop="configName" label="名称" />
            <el-table-column prop="configValue" label="值" />
            <el-table-column prop="createdBy" label="创建人" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="primary" @click="openConfig(row)">编辑</el-button>
                <el-button link type="danger" @click="removeConfig(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="公告" name="notice">
          <div class="toolbar-inline">
            <el-button type="primary" @click="openNotice()">新增公告</el-button>
          </div>
          <el-table :data="notices" stripe>
            <el-table-column prop="noticeTitle" label="标题" min-width="160" />
            <el-table-column prop="noticeContent" label="内容" min-width="220" show-overflow-tooltip />
            <el-table-column label="发布" width="90">
              <template #default="{ row }">{{ row.published ? '已发布' : '草稿' }}</template>
            </el-table-column>
            <el-table-column prop="createdBy" label="创建人" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="primary" @click="openNotice(row)">编辑</el-button>
                <el-button link type="danger" @click="removeNotice(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="dictVisible" title="字典表单" width="520px">
      <el-form label-position="top">
        <el-form-item label="字典类型"><el-input v-model="dictForm.dictType" /></el-form-item>
        <el-form-item label="字典编码"><el-input v-model="dictForm.dictCode" /></el-form-item>
        <el-form-item label="字典值"><el-input v-model="dictForm.dictValue" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDict">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="configVisible" title="参数表单" width="520px">
      <el-form label-position="top">
        <el-form-item label="参数键"><el-input v-model="configForm.configKey" /></el-form-item>
        <el-form-item label="参数名称"><el-input v-model="configForm.configName" /></el-form-item>
        <el-form-item label="参数值"><el-input v-model="configForm.configValue" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConfig">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="noticeVisible" title="公告表单" width="620px">
      <el-form label-position="top">
        <el-form-item label="公告标题"><el-input v-model="noticeForm.noticeTitle" /></el-form-item>
        <el-form-item label="公告内容"><el-input v-model="noticeForm.noticeContent" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="发布时间">
          <el-date-picker v-model="noticeForm.publishTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="是否发布">
          <el-switch v-model="noticeForm.published" inline-prompt active-text="发布" inactive-text="草稿" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noticeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNotice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createConfig,
  createDict,
  createNotice,
  deleteConfig,
  deleteDict,
  deleteNotice,
  queryConfigs,
  queryDicts,
  queryFeatures,
  queryNotices,
  updateConfig,
  updateDict,
  updateNotice,
} from '@/api/system'
import type { ConfigView, DictView, FeatureFlags, NoticeView } from '@/types/auth'

const activeTab = ref('dict')
const dicts = ref<DictView[]>([])
const configs = ref<ConfigView[]>([])
const notices = ref<NoticeView[]>([])
const features = ref<FeatureFlags | null>(null)

const dictVisible = ref(false)
const configVisible = ref(false)
const noticeVisible = ref(false)

const editingDictId = ref<number | null>(null)
const editingConfigId = ref<number | null>(null)
const editingNoticeId = ref<number | null>(null)

const dictForm = reactive({ dictType: '', dictCode: '', dictValue: '' })
const configForm = reactive({ configKey: '', configName: '', configValue: '' })
const noticeForm = reactive({ noticeTitle: '', noticeContent: '', published: false, publishTime: '' })

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
  const [dictList, configList, noticeList, featureData] = await Promise.all([
    queryDicts(),
    queryConfigs(),
    queryNotices(),
    queryFeatures(),
  ])
  dicts.value = dictList
  configs.value = configList
  notices.value = noticeList
  features.value = featureData
}

function openDict(row?: DictView) {
  editingDictId.value = row?.id ?? null
  Object.assign(dictForm, row ?? { dictType: '', dictCode: '', dictValue: '' })
  dictVisible.value = true
}

function openConfig(row?: ConfigView) {
  editingConfigId.value = row?.id ?? null
  Object.assign(configForm, row ?? { configKey: '', configName: '', configValue: '' })
  configVisible.value = true
}

function openNotice(row?: NoticeView) {
  editingNoticeId.value = row?.id ?? null
  Object.assign(noticeForm, {
    noticeTitle: row?.noticeTitle ?? '',
    noticeContent: row?.noticeContent ?? '',
    published: row?.published ?? false,
    publishTime: row?.publishTime ?? '',
  })
  noticeVisible.value = true
}

async function submitDict() {
  if (editingDictId.value) {
    await updateDict(editingDictId.value, dictForm)
    ElMessage.success('字典已更新')
  } else {
    await createDict(dictForm)
    ElMessage.success('字典已创建')
  }
  dictVisible.value = false
  await load()
}

async function submitConfig() {
  if (editingConfigId.value) {
    await updateConfig(editingConfigId.value, configForm)
    ElMessage.success('参数已更新')
  } else {
    await createConfig(configForm)
    ElMessage.success('参数已创建')
  }
  configVisible.value = false
  await load()
}

async function submitNotice() {
  if (editingNoticeId.value) {
    await updateNotice(editingNoticeId.value, noticeForm)
    ElMessage.success('公告已更新')
  } else {
    await createNotice(noticeForm)
    ElMessage.success('公告已创建')
  }
  noticeVisible.value = false
  await load()
}

async function removeDict(id: number) {
  await confirmDelete()
  await deleteDict(id)
  ElMessage.success('字典已删除')
  await load()
}

async function removeConfig(id: number) {
  await confirmDelete()
  await deleteConfig(id)
  ElMessage.success('参数已删除')
  await load()
}

async function removeNotice(id: number) {
  await confirmDelete()
  await deleteNotice(id)
  ElMessage.success('公告已删除')
  await load()
}

function confirmDelete() {
  return ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除确认', {
    type: 'warning',
  })
}
</script>
