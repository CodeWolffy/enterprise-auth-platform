<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/AdvancedSearch.vue'
import { useTablePreferences } from '@/composables/useTablePreferences'
import { queryConsents, revokeConsent } from '@/api/authConsents'
import type { ConsentView } from '@/api/authConsents'

const route = useRoute()
const router = useRouter()

const searchForm = ref({
  clientId: typeof route.query.clientId === 'string' ? route.query.clientId : '',
  principalName: '',
})
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loading = ref(false)
const consents = ref<ConsentView[]>([])

const consentTablePrefs = useTablePreferences('eap.table.consents', [
  { key: 'principalName', label: '授权用户', width: 140 },
  { key: 'client', label: '客户端', width: 220 },
  { key: 'scopes', label: '已授权作用域', width: 280 },
  { key: 'audit', label: '审计联动', width: 260 },
  { key: 'actions', label: '操作', width: 120 },
])

const filteredClientLabel = computed(() => searchForm.value.clientId || '全部客户端')
const auditedCount = computed(() => consents.value.reduce((sum, item) => sum + item.auditEventCount, 0))

watch(
  () => route.query.clientId,
  (value) => {
    searchForm.value.clientId = typeof value === 'string' ? value : ''
    page.value = 1
    void load()
  },
)

async function load() {
  loading.value = true
  try {
    const result = await queryConsents(
      page.value,
      size.value,
      searchForm.value.clientId || undefined,
      searchForm.value.principalName || undefined,
    )
    consents.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  void load()
}

function resetSearch() {
  searchForm.value.clientId = ''
  searchForm.value.principalName = ''
  page.value = 1
  void router.replace({ name: 'consents', query: {} })
  void load()
}

function handleSizeChange(value: number) {
  size.value = value
  page.value = 1
  void load()
}

function handleCurrentChange(value: number) {
  page.value = value
  void load()
}

function onConsentHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  consentTablePrefs.setColumnWidth(key, newWidth)
}

async function handleRevoke(row: ConsentView) {
  try {
    await ElMessageBox.confirm(
      `确定要撤销用户 ${row.principalName} 对客户端 ${row.clientName} 的授权吗？此操作不可恢复。`,
      '撤销确认',
      { type: 'warning' },
    )
    await revokeConsent(row.registeredClientId, row.principalName)
    ElMessage.success('授权已撤销')
    void load()
  } catch (error) {
    if (error !== 'cancel') {
      throw error
    }
  }
}

void load()
</script>

<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Consents</span>
        <strong>{{ total }}</strong>
        <span>当前筛选条件下的授权记录总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Client</span>
        <strong>{{ filteredClientLabel }}</strong>
        <span>正在查看的客户端范围</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Audited</span>
        <strong>{{ auditedCount }}</strong>
        <span>当前页关联的授权审计事件数</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Authorization Consents</span>
          <h3>授权记录</h3>
        </div>
      </div>

      <AdvancedSearch @search="handleSearch" @reset="resetSearch">
        <el-form-item label="授权用户">
          <el-input
            v-model="searchForm.principalName"
            placeholder="输入用户名，支持模糊匹配"
            clearable
            @clear="handleSearch"
          />
        </el-form-item>
        <el-form-item label="客户端 ID">
          <el-input
            v-model="searchForm.clientId"
            placeholder="输入客户端 Client ID"
            clearable
            @clear="handleSearch"
          />
        </el-form-item>
      </AdvancedSearch>

      <div class="table-tools">
        <el-radio-group v-model="consentTablePrefs.density" size="small">
          <el-radio-button value="compact">紧凑</el-radio-button>
          <el-radio-button value="default">默认</el-radio-button>
          <el-radio-button value="comfortable">宽松</el-radio-button>
        </el-radio-group>
        <el-popover placement="bottom-end" width="240" trigger="click">
          <template #reference>
            <el-button size="small">列显示</el-button>
          </template>
          <div class="column-chooser">
            <el-checkbox
              v-for="item in consentTablePrefs.columns"
              :key="item.key"
              :model-value="consentTablePrefs.visibleColumnMap[item.key]"
              @change="(value: boolean) => consentTablePrefs.setColumnVisible(item.key, value)"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </el-popover>
        <el-button size="small" @click="consentTablePrefs.reset()">恢复默认</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="consents"
        stripe
        :class="`table-density-${consentTablePrefs.density}`"
        @header-dragend="onConsentHeaderDragEnd"
      >
        <el-table-column
          v-if="consentTablePrefs.visibleColumnMap.principalName"
          column-key="principalName"
          prop="principalName"
          label="授权用户"
          min-width="140"
          :width="consentTablePrefs.getColumnWidth('principalName')"
        />
        <el-table-column
          v-if="consentTablePrefs.visibleColumnMap.client"
          column-key="client"
          label="客户端"
          min-width="220"
          :width="consentTablePrefs.getColumnWidth('client')"
        >
          <template #default="{ row }">
            <div class="client-cell">
              <strong>{{ row.clientName }}</strong>
              <small>{{ row.clientId }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="consentTablePrefs.visibleColumnMap.scopes"
          column-key="scopes"
          label="已授权作用域"
          min-width="280"
          :width="consentTablePrefs.getColumnWidth('scopes')"
        >
          <template #default="{ row }">
            <el-tag v-for="scope in row.authorities" :key="scope" size="small" class="scope-tag">
              {{ scope }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="consentTablePrefs.visibleColumnMap.audit"
          column-key="audit"
          label="审计联动"
          min-width="260"
          :width="consentTablePrefs.getColumnWidth('audit')"
        >
          <template #default="{ row }">
            <div class="client-cell">
              <small>租户：{{ row.tenantId }}</small>
              <small>最近授权：{{ row.lastGrantedAt || '-' }}</small>
              <small>最近撤销：{{ row.lastRevokedAt || '-' }}</small>
              <small>关联审计：{{ row.auditEventCount }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="consentTablePrefs.visibleColumnMap.actions"
          column-key="actions"
          label="操作"
          fixed="right"
          :width="consentTablePrefs.getColumnWidth('actions') || 120"
        >
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRevoke(row)">撤销授权</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.client-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;

  small {
    color: #667085;
  }
}

.scope-tag {
  margin-right: 6px;
  margin-bottom: 6px;
}

.table-tools {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  margin: -4px 0 10px;
}

.column-chooser {
  display: grid;
  gap: 8px;
  max-height: 280px;
  overflow: auto;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
