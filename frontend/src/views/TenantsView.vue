<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Tenants</span>
        <strong>{{ total }}</strong>
        <span>当前筛选条件下的租户数</span>
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

      <el-form :inline="true" class="toolbar-inline" @submit.prevent="handleSearch">
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
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tenants" stripe>
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
        <el-table-column label="套餐" min-width="150">
          <template #default="{ row }">{{ getPackageLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="用户配额" min-width="100">
          <template #default="{ row }">{{ row.userQuota ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="存储配额(GB)" min-width="120">
          <template #default="{ row }">{{ row.storageQuotaGb ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="到期提醒" min-width="220">
          <template #default="{ row }">
            <template v-if="row.expireAt">
              <el-tag :type="getExpireTagType(row.expireAt)">{{ getExpireText(row.expireAt) }}</el-tag>
              <span class="expire-date">{{ row.expireAt.split('T')[0] }}</span>
            </template>
            <el-tag v-else type="info">无限期</el-tag>
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

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="租户详情" size="720px">
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
          <el-descriptions-item label="套餐">{{ getPackageLabel(detailTenant) }}</el-descriptions-item>
          <el-descriptions-item label="套餐编码">{{ detailTenant.packageCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户配额">{{ detailTenant.userQuota ?? '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="存储配额(GB)">{{ detailTenant.storageQuotaGb ?? '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="到期状态">
            {{ detailTenant.expireAt ? getExpireText(detailTenant.expireAt) : '无限期' }}
          </el-descriptions-item>
          <el-descriptions-item label="到期时间" :span="2">
            {{ detailTenant.expireAt || '未设置到期时间' }}
          </el-descriptions-item>
          <el-descriptions-item label="运营备注" :span="2">
            {{ detailTenant.lifecycleNote || '未填写' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="capability-grid">
          <article v-for="capability in getCapabilityList(detailTenant)" :key="capability.label" class="capability-card">
            <strong>{{ capability.label }}</strong>
            <span>{{ capability.value }}</span>
          </article>
        </div>
        <div v-if="detailTenant.capabilityCodes?.length" class="capability-tags">
          <el-tag v-for="code in detailTenant.capabilityCodes" :key="code" type="success" effect="plain">
            {{ code }}
          </el-tag>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="visible" :title="editingTenantId ? '编辑租户' : '新增租户'" width="680px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="tenantRules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="租户编码" prop="tenantId">
              <el-input v-model="form.tenantId" :disabled="Boolean(editingTenantId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租户名称" prop="tenantName">
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
            <el-form-item label="租户状态" prop="tenantStatus">
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
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="套餐编码">
              <el-input v-model="form.packageCode" placeholder="如 business-standard" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="套餐名称">
              <el-input v-model="form.packageName" placeholder="如 标准版" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户配额">
              <el-input-number v-model="form.userQuota" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存储配额(GB)">
              <el-input-number v-model="form.storageQuotaGb" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="启用能力">
          <el-select v-model="form.capabilityCodes" multiple allow-create default-first-option style="width: 100%">
            <el-option label="oauth" value="oauth" />
            <el-option label="user" value="user" />
            <el-option label="role" value="role" />
            <el-option label="dept" value="dept" />
            <el-option label="tenant" value="tenant" />
            <el-option label="system" value="system" />
            <el-option label="audit" value="audit" />
            <el-option label="notice" value="notice" />
          </el-select>
        </el-form-item>
        <el-form-item label="运营备注">
          <el-input v-model="form.lifecycleNote" type="textarea" :rows="3" maxlength="200" show-word-limit />
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
import type { FormInstance, FormRules } from 'element-plus'
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
const page = ref(1)
const size = ref(10)
const total = ref(0)
const formRef = ref<FormInstance>()

const form = reactive({
  tenantId: '',
  tenantName: '',
  platformLevel: false,
  tenantStatus: 1,
  expireAt: '',
  packageCode: '',
  packageName: '',
  userQuota: undefined as number | undefined,
  storageQuotaGb: undefined as number | undefined,
  capabilityCodes: [] as string[],
  lifecycleNote: '',
})

const tenantRules = reactive<FormRules>({
  tenantId: [
    { required: true, message: '请输入租户编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]{2,64}$/, message: '租户编码仅支持字母、数字、:、_、-', trigger: 'blur' },
  ],
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  tenantStatus: [{ required: true, message: '请选择租户状态', trigger: 'change' }],
})

const platformTenantCount = computed(() => tenants.value.filter((item) => item.platformLevel).length)
const enabledTenantCount = computed(() => tenants.value.filter((item) => item.tenantStatus === 1).length)
const expiringSoonCount = computed(
  () =>
    tenants.value.filter((item) => {
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
    const result = await queryTenants({
      keyword: keyword.value || undefined,
      platformLevel: platformFilter.value ? platformFilter.value === 'platform' : undefined,
      tenantStatus: statusFilter.value ?? undefined,
      page: page.value,
      size: size.value,
    })
    tenants.value = result.records
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
  keyword.value = ''
  platformFilter.value = ''
  statusFilter.value = null
  page.value = 1
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

function getPackageLabel(tenant: TenantView) {
  return tenant.packageName || tenant.packageCode || (tenant.platformLevel ? '平台治理版' : '未配置套餐')
}

function getCapabilityList(tenant: TenantView) {
  return [
    { label: '能力范围', value: tenant.capabilityCodes?.length ? tenant.capabilityCodes.join('、') : '未配置能力集合' },
    { label: '套餐建议', value: getPackageLabel(tenant) },
    { label: '运营提示', value: tenant.lifecycleNote || (tenant.expireAt ? getExpireText(tenant.expireAt) : '建议补充套餐与配额信息') },
  ]
}

function openTenant(row?: TenantView) {
  editingTenantId.value = row?.tenantId ?? null
  Object.assign(form, {
    tenantId: row?.tenantId ?? '',
    tenantName: row?.name ?? '',
    platformLevel: row?.platformLevel ?? false,
    tenantStatus: row?.tenantStatus ?? 1,
    expireAt: row?.expireAt ?? '',
    packageCode: row?.packageCode ?? '',
    packageName: row?.packageName ?? '',
    userQuota: row?.userQuota ?? undefined,
    storageQuotaGb: row?.storageQuotaGb ?? undefined,
    capabilityCodes: [...(row?.capabilityCodes ?? [])],
    lifecycleNote: row?.lifecycleNote ?? '',
  })
  visible.value = true
}

function openDetail(row: TenantView) {
  detailTenant.value = row
  detailVisible.value = true
}

async function submit() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
  const payload = {
    tenantId: form.tenantId,
    tenantName: form.tenantName,
    platformLevel: form.platformLevel,
    tenantStatus: form.tenantStatus,
    expireAt: form.expireAt || null,
    packageCode: form.packageCode || null,
    packageName: form.packageName || null,
    userQuota: form.userQuota ?? null,
    storageQuotaGb: form.storageQuotaGb ?? null,
    capabilityCodes: form.capabilityCodes,
    lifecycleNote: form.lifecycleNote || null,
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

.capability-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  margin-top: 20px;
}

.capability-card {
  display: grid;
  gap: 8px;
  padding: 16px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.9);
  color: #475569;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.capability-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}
</style>
