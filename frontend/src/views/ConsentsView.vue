<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const filteredClientLabel = computed(() => searchForm.value.clientId || '全部客户端')

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
  void load()
}

function handleCurrentChange(value: number) {
  page.value = value
  void load()
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
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Authorization Consents</span>
          <h3>授权记录</h3>
        </div>
      </div>

      <div class="toolbar-inline">
        <el-form :inline="true" :model="searchForm" @submit.prevent="handleSearch">
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
          <el-form-item>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table v-loading="loading" :data="consents" stripe>
        <el-table-column prop="principalName" label="授权用户" min-width="140" />
        <el-table-column label="客户端" min-width="220">
          <template #default="{ row }">
            <div class="client-cell">
              <strong>{{ row.clientName }}</strong>
              <small>{{ row.clientId }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="已授权作用域" min-width="280">
          <template #default="{ row }">
            <el-tag v-for="scope in row.authorities" :key="scope" size="small" class="scope-tag">
              {{ scope }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
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

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
