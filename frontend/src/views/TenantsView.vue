<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Tenants</span>
        <strong>{{ filteredTenants.length }}</strong>
        <span>当前筛选条件下租户总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Platform</span>
        <strong>{{ platformTenantCount }}</strong>
        <span>平台级租户数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Enabled</span>
        <strong>{{ enabledTenantCount }}</strong>
        <span>启用中的租户数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Expiring</span>
        <strong>{{ expiringSoonCount }}</strong>
        <span>30 天内到期的租户数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Tenants</span>
          <h3>租户管理</h3>
        </div>
        <el-button type="primary" @click="openTenant()">新增租户</el-button>
      </div>

      <el-form :inline="true" class="toolbar-inline" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="搜索租户编码或名称" clearable />
        </el-form-item>
        <el-form-item label="租户级别">
          <el-select v-model="platformFilter" placeholder="全部" clearable style="width: 160px">
            <el-option label="全部" value="" />
            <el-option label="平台级租户" value="platform" />
            <el-option label="业务租户" value="business" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 140px">
            <el-option label="全部" :value="null" />
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="filteredTenants" stripe>
        <el-table-column prop="tenantId" label="租户编码" min-width="140" />
        <el-table-column prop="name" label="租户名称" min-width="160" />
        <el-table-column label="租户级别" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.platformLevel ? 'danger' : 'success'">
              {{ row.platformLevel ? '平台级租户' : '业务租户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <el-tag :type="row.tenantStatus === 1 ? 'success' : 'info'">
              {{ row.tenantStatus === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到期提醒" min-width="220">
          <template #default="{ row }">
            <template v-if="row.expireAt">
              <el-tag :type="getExpireTagType(row.expireAt)">{{ getExpireText(row.expireAt) }}</el-tag>
              <span class="expire-date">{{ row.expireAt.split('T')[0] }}</span>
            </template>
            <el-tag v-else type="info">无限制</el-tag>
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openTenant(row)">编辑</el-button>
            <el-button link type="danger" @click="removeTenant(row.tenantId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" title="租户详情" size="600px">
      <template v-if="detailTenant">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="租户编码">{{ detailTenant.tenantId }}</el-descriptions-item>
          <el-descriptions-item label="租户名称">{{ detailTenant.name }}</el-descriptions-item>
          <el-descriptions-item label="租户级别">
            {{ detailTenant.platformLevel ? '平台级租户' : '业务租户' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ detailTenant.tenantStatus === 1 ? '启用' : '禁用' }}
          </el-descriptions-item>
          <el-descriptions-item label="到期状态">
            {{ detailTenant.expireAt ? getExpireText(detailTenant.expireAt) : '无限制' }}
          </el-descriptions-item>
          <el-descriptions-item label="到期时间" :span="2">
            {{ detailTenant.expireAt || '未设置到期时间' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-tip">
          <el-alert
            :title="detailTenant.platformLevel ? '该租户为平台级租户，可承载全局治理能力。' : '该租户为业务租户，建议关注到期时间与账号活跃情况。'"
            type="info"
            :closable="false"
          />
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="visible" :title="editingTenantId ? '编辑租户' : '新增租户'" width="660px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="租户编码">
              <el-input v-model="form.tenantId" :disabled="Boolean(editingTenantId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租户名称">
              <el-input v-model="form.tenantName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="租户级别">
              <el-switch v-model="form.platformLevel" inline-prompt active-text="平台级" inactive-text="业务级" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租户状态">
              <el-select v-model="form.tenantStatus" style="width: 100%">
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="到期时间">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
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
import { createTenant, deleteTenant, queryTenants, updateTenant } from '@/api/platform'
import type { TenantView } from '@/types/auth'

const tenants = ref<TenantView[]>([])
const visible = ref(false)
const detailVisible = ref(false)
const editingTenantId = ref<string | null>(null)
const detailTenant = ref<TenantView | null>(null)
const loading = ref(false)
const keyword = ref('')
const platformFilter = ref('')
const statusFilter = ref<number | null>(null)

const form = reactive({
  tenantId: '',
  tenantName: '',
  platformLevel: false,
  tenantStatus: 1,
  expireAt: '',
})

const filteredTenants = computed(() =>
  tenants.value.filter((tenant) => {
    const normalizedKeyword = keyword.value.trim().toLowerCase()
    const matchesKeyword =
      !normalizedKeyword ||
      [tenant.tenantId, tenant.name].some((value) => value.toLowerCase().includes(normalizedKeyword))
    const matchesPlatform =
      !platformFilter.value ||
      (platformFilter.value === 'platform' ? tenant.platformLevel : !tenant.platformLevel)
    const matchesStatus = statusFilter.value === null || tenant.tenantStatus === statusFilter.value
    return matchesKeyword && matchesPlatform && matchesStatus
  }),
)

const platformTenantCount = computed(() => filteredTenants.value.filter((item) => item.platformLevel).length)
const enabledTenantCount = computed(() => filteredTenants.value.filter((item) => item.tenantStatus === 1).length)
const expiringSoonCount = computed(
  () =>
    filteredTenants.value.filter((item) => {
      if (!item.expireAt) {
        return false
      }
      const diff = new Date(item.expireAt).getTime() - Date.now()
      return diff >= 0 && diff <= 30 * 24 * 3600 * 1000
    }).length,
)

void load()

async function load() {
  loading.value = true
  try {
    tenants.value = await queryTenants()
  } finally {
    loading.value = false
  }
}

function getExpireTagType(expireAt: string) {
  const time = new Date(expireAt).getTime()
  const now = Date.now()
  const days = (time - now) / (1000 * 3600 * 24)
  if (days < 0) return 'danger'
  if (days < 30) return 'warning'
  return 'success'
}

function getExpireText(expireAt: string) {
  const time = new Date(expireAt).getTime()
  const now = Date.now()
  const days = Math.floor((time - now) / (1000 * 3600 * 24))
  if (days < 0) return `已超期 ${-days} 天`
  if (days < 30) return `即将到期（剩余 ${days} 天）`
  return '有效中'
}

function openTenant(row?: TenantView) {
  editingTenantId.value = row?.tenantId ?? null
  Object.assign(form, {
    tenantId: row?.tenantId ?? '',
    tenantName: row?.name ?? '',
    platformLevel: row?.platformLevel ?? false,
    tenantStatus: row?.tenantStatus ?? 1,
    expireAt: row?.expireAt ?? '',
  })
  visible.value = true
}

function openDetail(row: TenantView) {
  detailTenant.value = row
  detailVisible.value = true
}

async function submit() {
  const payload = {
    tenantId: form.tenantId,
    tenantName: form.tenantName,
    platformLevel: form.platformLevel,
    tenantStatus: form.tenantStatus,
    expireAt: form.expireAt || null,
  }
  if (editingTenantId.value) {
    await updateTenant(editingTenantId.value, payload)
    ElMessage.success('租户已更新')
  } else {
    await createTenant(payload)
    ElMessage.success('租户已创建')
  }
  visible.value = false
  await load()
}

async function removeTenant(tenantId: string) {
  await ElMessageBox.confirm('删除租户后，相关租户数据将无法继续访问，是否继续？', '删除确认', { type: 'warning' })
  await deleteTenant(tenantId)
  ElMessage.success('租户已删除')
  await load()
}
</script>

<style scoped lang="scss">
.expire-date {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.detail-tip {
  margin-top: 20px;
}
</style>
