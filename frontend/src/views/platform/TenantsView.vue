<template>
  <div class="tenants-view">
    <section class="summary-strip">
      <article>
        <strong>{{ total }}</strong>
        <span>租户总数</span>
      </article>
      <article>
        <strong>{{ enabledTenantCount }}</strong>
        <span>当前页启用</span>
      </article>
      <article>
        <strong>{{ platformTenantCount }}</strong>
        <span>平台级租户</span>
      </article>
    </section>

    <section class="work-panel">
      <div class="panel-head">
        <div>
          <h1>租户管理</h1>
        </div>
        <div class="panel-actions">
          <span class="muted">{{ refreshText }}</span>
          <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
          <el-button v-permission="'upms:systenant:add'" :icon="Plus" type="primary" @click="openTenant()">新增租户</el-button>
        </div>
      </div>

      <AdvancedSearch @search="handleSearch" @reset="resetSearch">
        <el-form-item label="关键字">
          <el-input v-model="keyword" clearable placeholder="租户编码或名称" />
        </el-form-item>
        <el-form-item label="租户级别">
          <el-select v-model="platformFilter" clearable style="width: 160px">
            <el-option label="全部" value="" />
            <el-option label="平台级租户" value="platform" />
            <el-option label="业务租户" value="business" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="启用" value="1" />
            <el-option label="禁用" value="0" />
          </el-select>
        </el-form-item>
      </AdvancedSearch>

      <el-table v-loading="loading" :data="tenants" stripe row-key="tenantId">
        <el-table-column prop="tenantId" label="租户编码" min-width="150" />
        <el-table-column prop="name" label="租户名称" min-width="170" />
        <el-table-column label="级别" width="110">
          <template #default="{ row }">
            <el-tag :type="row.platformLevel ? 'danger' : 'success'">{{ row.platformLevel ? '平台级' : '业务级' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.tenantStatus === 1 ? 'success' : 'info'">{{ row.tenantStatus === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="套餐" min-width="180">
          <template #default="{ row }">{{ row.packageName || row.packageCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="授权期限" min-width="220">
          <template #default="{ row }">
            <div class="stacked">
              <span>{{ formatDateTime(row.authBeginAt) || '立即生效' }}</span>
              <small>至 {{ formatDateTime(row.expireAt) || '长期有效' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="联系人" min-width="180">
          <template #default="{ row }">
            <div class="stacked">
              <span>{{ row.contactName || '-' }}</span>
              <small>{{ row.contactPhone || row.contactEmail || '未填写联系方式' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" :icon="Clock" @click="openHistory(row)">历史</el-button>
            <el-button v-permission="'upms:systenant:edit'" link type="primary" :icon="Menu" @click="openMenuDrawer(row)">菜单</el-button>
            <el-button v-permission="'upms:systenant:edit'" link type="primary" :icon="Edit" @click="openTenant(row)">编辑</el-button>
            <el-button v-permission="'upms:systenant:del'" link type="danger" :icon="Delete" @click="removeTenant(row.tenantId)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无租户数据" />
        </template>
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

    <el-dialog v-model="visible" :title="editingTenantId ? '编辑租户' : '新增租户'" width="760px">
      <el-form ref="formRef" :model="form" :rules="tenantRules" label-position="top">
        <div class="form-grid">
          <el-form-item label="租户编码" prop="tenantId">
            <el-input v-model="form.tenantId" :disabled="Boolean(editingTenantId)" maxlength="64" />
          </el-form-item>
          <el-form-item label="租户名称" prop="tenantName">
            <el-input v-model="form.tenantName" maxlength="80" />
          </el-form-item>
          <el-form-item label="租户级别" prop="platformLevel">
            <el-switch v-model="form.platformLevel" inline-prompt active-text="平台" inactive-text="业务" />
          </el-form-item>
          <el-form-item label="状态" prop="tenantStatus">
            <el-select v-model="form.tenantStatus" style="width: 100%">
              <el-option label="启用" :value="1" />
              <el-option label="禁用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权开始" prop="authBeginAt">
            <el-date-picker v-model="form.authBeginAt" type="datetime" style="width: 100%" />
          </el-form-item>
          <el-form-item label="授权结束" prop="expireAt">
            <el-date-picker v-model="form.expireAt" type="datetime" style="width: 100%" />
          </el-form-item>
          <el-form-item label="套餐" prop="packageCode">
            <el-select v-model="form.packageCode" clearable filterable style="width: 100%" @change="syncPackage">
              <el-option v-for="pkg in selectablePackageOptions" :key="pkg.packageCode" :label="`${pkg.packageName} (${pkg.packageCode})`" :value="pkg.packageCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="套餐名称">
            <el-input v-model="form.packageName" disabled />
          </el-form-item>
          <el-form-item label="联系人">
            <el-input v-model="form.contactName" maxlength="80" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="form.contactPhone" maxlength="40" />
          </el-form-item>
          <el-form-item label="联系邮箱">
            <el-input v-model="form.contactEmail" maxlength="120" />
          </el-form-item>
          <el-form-item label="Logo 地址">
            <el-input v-model="form.logoUrl" maxlength="255" />
          </el-form-item>
        </div>
        <el-form-item label="官网">
          <el-input v-model="form.website" maxlength="255" />
        </el-form-item>
        <el-form-item label="联系地址">
          <el-input v-model="form.address" maxlength="255" />
        </el-form-item>
        <el-form-item label="运营备注">
          <el-input v-model="form.lifecycleNote" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button v-permission="['upms:systenant:add', 'upms:systenant:edit']" type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="租户详情" size="760px">
      <template v-if="detailTenant">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="租户编码">{{ detailTenant.tenantId }}</el-descriptions-item>
          <el-descriptions-item label="租户名称">{{ detailTenant.name }}</el-descriptions-item>
          <el-descriptions-item label="套餐">{{ detailTenant.packageName || detailTenant.packageCode || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailTenant.tenantStatus === 1 ? '启用' : '禁用' }}</el-descriptions-item>
          <el-descriptions-item label="授权开始">{{ formatDateTime(detailTenant.authBeginAt) || '立即生效' }}</el-descriptions-item>
          <el-descriptions-item label="授权结束">{{ formatDateTime(detailTenant.expireAt) || '长期有效' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detailTenant.contactName || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detailTenant.contactPhone || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="联系邮箱">{{ detailTenant.contactEmail || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="官网">{{ detailTenant.website || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ detailTenant.address || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="运营备注" :span="2">{{ detailTenant.lifecycleNote || '未填写' }}</el-descriptions-item>
        </el-descriptions>

        <div class="drawer-section" v-loading="detailLoading">
          <div class="drawer-title">
            <h3>租户菜单范围</h3>
            <el-tag>{{ detailMenuIds.length }} 项</el-tag>
          </div>
          <el-tree :data="detailMenuTree" :props="treeProps" node-key="id" default-expand-all>
            <template #default="{ data }">
              <span class="tree-node">
                <span>{{ data.name }}</span>
                <el-tag size="small" :type="data.type === '1' ? 'warning' : 'info'" effect="plain">{{ data.type === '1' ? '按钮' : '菜单' }}</el-tag>
              </span>
            </template>
          </el-tree>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="menuVisible" title="租户菜单授权" size="760px" @opened="applyCheckedMenuIds">
      <template v-if="menuTenant">
        <div class="drawer-title">
          <h3>{{ menuTenant.name }}</h3>
          <span class="muted">{{ menuTenant.tenantId }}</span>
        </div>
        <el-tree
          ref="menuTreeRef"
          v-loading="menuLoading"
          :data="menuTree"
          :props="treeProps"
          node-key="id"
          show-checkbox
          default-expand-all
        >
          <template #default="{ data }">
            <span class="tree-node">
              <span>{{ data.name }}</span>
              <el-tag v-if="data.permission" size="small" effect="plain">{{ data.permission }}</el-tag>
              <el-tag size="small" :type="data.type === '1' ? 'warning' : 'info'" effect="plain">{{ data.type === '1' ? '按钮' : '菜单' }}</el-tag>
            </span>
          </template>
        </el-tree>
      </template>
      <template #footer>
        <el-button @click="menuVisible = false">取消</el-button>
        <el-button v-permission="'upms:systenant:edit'" type="primary" :loading="menuSaving" @click="submitTenantMenus">保存菜单</el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="historyVisible" title="租户变更历史" size="860px">
      <AdvancedSearch @search="applyHistorySearch" @reset="resetHistorySearch">
        <el-form-item label="类型">
          <el-select v-model="historyQuery.changeType" clearable style="width: 160px">
            <el-option label="全部" value="" />
            <el-option label="创建" value="CREATED" />
            <el-option label="状态" value="STATUS" />
            <el-option label="套餐" value="PACKAGE" />
            <el-option label="菜单" value="MENU" />
            <el-option label="资料" value="PROFILE" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段">
          <el-input v-model="historyQuery.fieldKey" clearable placeholder="字段键" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="historyQuery.operator" clearable placeholder="操作人" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="historyDateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            clearable
          />
        </el-form-item>
      </AdvancedSearch>

      <div class="history-strip">
        <article>
          <strong>{{ historySummary.totalChanges }}</strong>
          <span>命中变更</span>
        </article>
        <article>
          <strong>{{ historySummary.packageChanges }}</strong>
          <span>套餐变更</span>
        </article>
        <article>
          <strong>{{ historySummary.menuChanges }}</strong>
          <span>菜单变更</span>
        </article>
        <article>
          <strong>{{ historySummary.statusChanges }}</strong>
          <span>状态变更</span>
        </article>
      </div>

      <el-table :data="historyRecords" stripe>
        <el-table-column prop="changeType" label="类型" min-width="110" />
        <el-table-column prop="fieldKey" label="字段" min-width="130" />
        <el-table-column prop="summary" label="摘要" min-width="220" />
        <el-table-column prop="impactSummary" label="影响说明" min-width="260" />
        <el-table-column prop="operator" label="操作人" min-width="120" />
        <el-table-column label="时间" min-width="180">
          <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="historyPage"
          v-model:page-size="historySize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="historyTotal"
          @size-change="handleHistorySizeChange"
          @current-change="handleHistoryPageChange"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue'
import { Clock, Delete, Edit, Menu, Plus, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import {
  createTenant,
  deleteTenant,
  queryMenuTemplateTree,
  queryTenantHistory,
  queryTenantHistorySummary,
  queryTenantMenus,
  queryTenantPackages,
  queryTenants,
  saveTenantMenus,
  updateTenant,
} from '@/api/modules'
import type { MenuTreeNode } from '@/api/modules/menu'
import type { TenantChangeView, TenantHistorySummaryView, TenantPackageView, TenantView } from '@/types/tenant'
import { formatDateTime, toDate, toEpochMs } from '@/utils/datetime'

const tenants = ref<TenantView[]>([])
const packageOptions = ref<TenantPackageView[]>([])
const menuTree = ref<MenuTreeNode[]>([])
const detailMenuTree = ref<MenuTreeNode[]>([])
const detailMenuIds = ref<number[]>([])
const loading = ref(false)
const visible = ref(false)
const detailVisible = ref(false)
const historyVisible = ref(false)
const menuVisible = ref(false)
const detailLoading = ref(false)
const menuLoading = ref(false)
const menuSaving = ref(false)
const editingTenantId = ref<string | null>(null)
const detailTenant = ref<TenantView | null>(null)
const menuTenant = ref<TenantView | null>(null)
const checkedMenuIds = ref<number[]>([])
const historyTenantId = ref<string | null>(null)
const historyRecords = ref<TenantChangeView[]>([])
const historySummary = ref<TenantHistorySummaryView>({
  tenantId: '',
  totalChanges: 0,
  packageChanges: 0,
  menuChanges: 0,
  statusChanges: 0,
  profileChanges: 0,
  affectedFieldCounts: {},
  recentTimeline: [],
})
const historyPage = ref(1)
const historySize = ref(10)
const historyTotal = ref(0)
const historyQuery = reactive({ changeType: '', fieldKey: '', operator: '' })
const historyDateRange = ref<[Date, Date] | null>(null)
const keyword = ref('')
const platformFilter = ref('')
const statusFilter = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const loadedAt = ref<number | null>(null)
const formRef = ref<FormInstance>()
const menuTreeRef = ref<any>(null)

const form = reactive({
  tenantId: '',
  tenantName: '',
  platformLevel: false,
  tenantStatus: 1,
  authBeginAt: null as Date | null,
  expireAt: null as Date | null,
  packageCode: '',
  packageName: '',
  logoUrl: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  website: '',
  address: '',
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

const treeProps = { label: 'name', children: 'children' }
const enabledTenantCount = computed(() => tenants.value.filter((item) => item.tenantStatus === 1).length)
const platformTenantCount = computed(() => tenants.value.filter((item) => item.platformLevel).length)
const refreshText = computed(() => (loadedAt.value ? `上次刷新：${formatDateTime(loadedAt.value)}` : ''))
const selectablePackageOptions = computed(() =>
  packageOptions.value.filter((item) => item.status === '0' || item.packageCode === form.packageCode),
)

void bootstrap()

async function bootstrap() {
  await Promise.all([load(), loadCatalog(), loadMenuTemplate()])
}

async function loadCatalog() {
  packageOptions.value = await queryTenantPackages()
}

async function loadMenuTemplate() {
  menuTree.value = await queryMenuTemplateTree()
}

async function load() {
  loading.value = true
  try {
    const result = await queryTenants({
      keyword: keyword.value || undefined,
      platformLevel: platformFilter.value ? platformFilter.value === 'platform' : undefined,
      tenantStatus: statusFilter.value === '' ? undefined : Number(statusFilter.value),
      page: page.value,
      size: size.value,
    })
    tenants.value = result.records
    total.value = result.total
    loadedAt.value = Date.now()
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
  statusFilter.value = ''
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

function openTenant(row?: TenantView) {
  editingTenantId.value = row?.tenantId ?? null
  Object.assign(form, {
    tenantId: row?.tenantId ?? '',
    tenantName: row?.name ?? '',
    platformLevel: row?.platformLevel ?? false,
    tenantStatus: row?.tenantStatus ?? 1,
    authBeginAt: toDate(row?.authBeginAt),
    expireAt: toDate(row?.expireAt),
    packageCode: row?.packageCode ?? '',
    packageName: row?.packageName ?? '',
    logoUrl: row?.logoUrl ?? '',
    contactName: row?.contactName ?? '',
    contactPhone: row?.contactPhone ?? '',
    contactEmail: row?.contactEmail ?? '',
    website: row?.website ?? '',
    address: row?.address ?? '',
    lifecycleNote: row?.lifecycleNote ?? '',
  })
  if (form.packageCode) {
    syncPackage(form.packageCode)
  }
  visible.value = true
}

function syncPackage(packageCode?: string) {
  const selected = packageOptions.value.find((item) => item.packageCode === packageCode)
  form.packageName = selected?.packageName ?? ''
}

async function submit() {
  await formRef.value?.validate()
  const payload = {
    tenantId: form.tenantId,
    tenantName: form.tenantName,
    platformLevel: form.platformLevel,
    tenantStatus: form.tenantStatus,
    authBeginAt: toEpochMs(form.authBeginAt),
    expireAt: toEpochMs(form.expireAt),
    packageCode: form.packageCode || null,
    packageName: form.packageName || null,
    logoUrl: form.logoUrl || null,
    contactName: form.contactName || null,
    contactPhone: form.contactPhone || null,
    contactEmail: form.contactEmail || null,
    website: form.website || null,
    address: form.address || null,
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

async function openDetail(row: TenantView) {
  detailTenant.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailMenuIds.value = await queryTenantMenus(row.tenantId)
    detailMenuTree.value = filterMenuTree(menuTree.value, new Set(detailMenuIds.value))
  } finally {
    detailLoading.value = false
  }
}

async function openMenuDrawer(row: TenantView) {
  menuTenant.value = row
  menuVisible.value = true
  menuLoading.value = true
  try {
    const [tree, assignedIds] = await Promise.all([queryMenuTemplateTree(), queryTenantMenus(row.tenantId)])
    menuTree.value = tree
    checkedMenuIds.value = assignedIds
    await nextTick()
    applyCheckedMenuIds()
  } finally {
    menuLoading.value = false
  }
}

function applyCheckedMenuIds() {
  menuTreeRef.value?.setCheckedKeys(checkedMenuIds.value)
}

async function submitTenantMenus() {
  if (!menuTenant.value) {
    return
  }
  const checked = ((menuTreeRef.value?.getCheckedKeys(false) || []) as Array<string | number>).map(Number)
  const halfChecked = ((menuTreeRef.value?.getHalfCheckedKeys() || []) as Array<string | number>).map(Number)
  const menuIds = [...new Set([...checked, ...halfChecked])].filter((id) => Number.isFinite(id))
  menuSaving.value = true
  try {
    await saveTenantMenus(menuTenant.value.tenantId, menuIds)
    ElMessage.success('租户菜单已更新')
    menuVisible.value = false
    if (detailTenant.value?.tenantId === menuTenant.value.tenantId) {
      detailMenuIds.value = menuIds
      detailMenuTree.value = filterMenuTree(menuTree.value, new Set(menuIds))
    }
  } finally {
    menuSaving.value = false
  }
}

async function removeTenant(tenantId: string) {
  await ElMessageBox.confirm('确认删除该租户吗？', '删除确认', { type: 'warning' })
  await deleteTenant(tenantId)
  ElMessage.success('租户已删除')
  await load()
}

function filterMenuTree(nodes: MenuTreeNode[], allowed: Set<number>): MenuTreeNode[] {
  return nodes
    .map((node) => {
      const children = filterMenuTree(node.children ?? [], allowed)
      if (!allowed.has(node.id) && children.length === 0) {
        return null
      }
      return { ...node, children }
    })
    .filter((node): node is MenuTreeNode => Boolean(node))
}

async function openHistory(row: TenantView) {
  historyTenantId.value = row.tenantId
  historyPage.value = 1
  historyVisible.value = true
  await Promise.all([loadHistory(), loadHistorySummary()])
}

function historyParams() {
  return {
    changeType: historyQuery.changeType || undefined,
    fieldKey: historyQuery.fieldKey || undefined,
    operator: historyQuery.operator || undefined,
    fromEpochMs: toEpochMs(historyDateRange.value?.[0]) ?? undefined,
    toEpochMs: toEpochMs(historyDateRange.value?.[1]) ?? undefined,
  }
}

async function loadHistory() {
  if (!historyTenantId.value) {
    return
  }
  const result = await queryTenantHistory(historyTenantId.value, {
    ...historyParams(),
    page: historyPage.value,
    size: historySize.value,
  })
  historyRecords.value = result.records
  historyTotal.value = result.total
}

async function loadHistorySummary() {
  if (!historyTenantId.value) {
    return
  }
  historySummary.value = await queryTenantHistorySummary(historyTenantId.value, historyParams())
}

async function applyHistorySearch() {
  historyPage.value = 1
  await Promise.all([loadHistory(), loadHistorySummary()])
}

async function resetHistorySearch() {
  historyQuery.changeType = ''
  historyQuery.fieldKey = ''
  historyQuery.operator = ''
  historyDateRange.value = null
  historyPage.value = 1
  await Promise.all([loadHistory(), loadHistorySummary()])
}

async function handleHistoryPageChange(value: number) {
  historyPage.value = value
  await loadHistory()
}

async function handleHistorySizeChange(value: number) {
  historySize.value = value
  historyPage.value = 1
  await loadHistory()
}
</script>

<style scoped>
.tenants-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-strip,
.history-strip {
  display: grid;
  gap: 12px;
}

.summary-strip {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.history-strip {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 12px 0;
}

.summary-strip article,
.history-strip article {
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.summary-strip strong,
.history-strip strong {
  display: block;
  font-size: 24px;
  line-height: 1.2;
}

.summary-strip span,
.history-strip span,
.muted,
.stacked small {
  color: var(--el-text-color-secondary);
}

.work-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel-head,
.panel-actions,
.drawer-title,
.tree-node {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-head {
  justify-content: space-between;
}

.panel-head h1,
.drawer-title h3 {
  margin: 0;
}

.stacked {
  display: grid;
  gap: 3px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.drawer-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 18px;
}

.tree-node {
  min-height: 28px;
}

@media (max-width: 720px) {
  .summary-strip,
  .history-strip,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .panel-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
