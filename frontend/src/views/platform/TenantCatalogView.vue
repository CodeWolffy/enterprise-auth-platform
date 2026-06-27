<template>
  <section class="tenant-catalog-view">
    <div class="page-head">
      <div>
        <h1>租户套餐</h1>
        <p>套餐通过应用标识限定默认菜单范围。</p>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" @click="loadPackages">刷新</el-button>
        <el-button v-permission="'upms:tenantpackage:add'" :icon="Plus" type="primary" @click="openPackageDialog()">新增套餐</el-button>
      </div>
    </div>

    <div class="summary-strip">
      <article>
        <strong>{{ packages.length }}</strong>
        <span>套餐总数</span>
      </article>
      <article>
        <strong>{{ enabledPackageCount }}</strong>
        <span>启用套餐</span>
      </article>
      <article>
        <strong>{{ referencedTenantCount }}</strong>
        <span>引用租户</span>
      </article>
    </div>

    <el-table v-loading="loading" :data="packages" stripe row-key="id">
      <el-table-column prop="packageCode" label="套餐编码" min-width="150" />
      <el-table-column prop="packageName" label="套餐名称" min-width="160" />
      <el-table-column label="应用标识" min-width="220">
        <template #default="{ row }">
          <div class="tag-wrap">
            <el-tag v-for="key in appKeys(row.appKey)" :key="key" effect="plain">{{ key }}</el-tag>
            <span v-if="!appKeys(row.appKey).length" class="muted">未配置</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '正常' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="referencedTenantCount" label="引用租户" width="110" />
      <el-table-column label="更新时间" min-width="170">
        <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" @click="openPackageDetail(row)">详情</el-button>
          <el-button v-permission="'upms:tenantpackage:edit'" link type="primary" :icon="Edit" @click="openPackageDialog(row)">编辑</el-button>
          <el-button v-permission="'upms:tenantpackage:del'" link type="danger" :icon="Delete" @click="removePackage(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="packageVisible" :title="editingPackageId ? '编辑套餐' : '新增套餐'" width="720px">
      <el-form ref="packageFormRef" :model="packageForm" :rules="packageRules" label-position="top">
        <div class="form-grid">
          <el-form-item label="套餐编码" prop="packageCode">
            <el-input v-model="packageForm.packageCode" :disabled="Boolean(editingPackageId)" maxlength="64" />
          </el-form-item>
          <el-form-item label="套餐名称" prop="packageName">
            <el-input v-model="packageForm.packageName" maxlength="80" />
          </el-form-item>
          <el-form-item label="应用标识" prop="appKey">
            <el-input v-model="packageForm.appKey" maxlength="200" />
          </el-form-item>
          <el-form-item label="展示排序" prop="orderNo">
            <el-input-number v-model="packageForm.orderNo" :min="0" :max="9999" style="width: 100%" />
          </el-form-item>
          <el-form-item label="销售价" prop="salesPrice">
            <el-input-number v-model="packageForm.salesPrice" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="原价" prop="originalPrice">
            <el-input-number v-model="packageForm.originalPrice" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="运营副标题" prop="subtitle">
          <el-input v-model="packageForm.subtitle" maxlength="120" />
        </el-form-item>
        <el-form-item label="套餐说明" prop="packageDesc">
          <el-input v-model="packageForm.packageDesc" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="富文本描述" prop="descriptionMd">
          <el-input v-model="packageForm.descriptionMd" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="packageForm.status" inline-prompt active-text="正常" inactive-text="停用" active-value="0" inactive-value="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="packageVisible = false">取消</el-button>
        <el-button v-permission="['upms:tenantpackage:add', 'upms:tenantpackage:edit']" type="primary" @click="submitPackage">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="packageDetailVisible" title="套餐详情" size="560px">
      <template v-if="detailPackage">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="套餐编码">{{ detailPackage.packageCode }}</el-descriptions-item>
          <el-descriptions-item label="套餐名称">{{ detailPackage.packageName }}</el-descriptions-item>
          <el-descriptions-item label="应用标识">
            <div class="tag-wrap">
              <el-tag v-for="key in appKeys(detailPackage.appKey)" :key="key" effect="plain">{{ key }}</el-tag>
              <span v-if="!appKeys(detailPackage.appKey).length" class="muted">未配置</span>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailPackage.status === '0' ? '正常' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="引用租户">{{ detailImpact?.referencedTenantCount ?? detailPackage.referencedTenantCount ?? 0 }}</el-descriptions-item>
        </el-descriptions>

        <div class="drawer-section" v-loading="detailLoading">
          <h3>影响分析</h3>
          <el-alert
            v-for="rule in detailImpact?.rules || []"
            :key="rule.ruleCode"
            :title="rule.message"
            :type="rule.level === 'ERROR' ? 'error' : 'warning'"
            show-icon
            :closable="false"
          />
          <div v-if="detailImpact?.referencedTenantIds?.length" class="tag-wrap">
            <el-tag v-for="tenantId in detailImpact.referencedTenantIds" :key="tenantId">{{ tenantId }}</el-tag>
          </div>
          <ul v-if="detailImpact?.recommendedActions?.length" class="action-list">
            <li v-for="item in detailImpact.recommendedActions" :key="item">{{ item }}</li>
          </ul>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createTenantPackage,
  deleteTenantPackage,
  queryTenantPackageImpact,
  queryTenantPackages,
  updateTenantPackage,
  type TenantPackagePayload,
} from '@/api/modules/tenantCatalog'
import type { TenantPackageImpactView, TenantPackageView } from '@/types/tenant'
import { formatDateTime } from '@/utils/datetime'

const loading = ref(false)
const detailLoading = ref(false)
const packages = ref<TenantPackageView[]>([])
const packageVisible = ref(false)
const packageDetailVisible = ref(false)
const editingPackageId = ref<number | null>(null)
const detailPackage = ref<TenantPackageView | null>(null)
const detailImpact = ref<TenantPackageImpactView | null>(null)
const packageFormRef = ref<FormInstance>()

const packageForm = reactive({
  packageCode: '',
  packageName: '',
  subtitle: '',
  salesPrice: undefined as number | undefined,
  originalPrice: undefined as number | undefined,
  descriptionMd: '',
  appKey: '',
  orderNo: 0,
  packageDesc: '',
  status: '0' as '0' | '1',
})

const packageRules = reactive<FormRules>({
  packageCode: [{ required: true, message: '请输入套餐编码', trigger: 'blur' }],
  packageName: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
})

const enabledPackageCount = computed(() => packages.value.filter((item) => item.status === '0').length)
const referencedTenantCount = computed(() =>
  packages.value.reduce((sum, item) => sum + (item.referencedTenantCount ?? 0), 0),
)

function appKeys(value?: string | null) {
  if (!value?.trim()) {
    return []
  }
  return value
    .split(/[,;\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function resetPackageForm(row?: TenantPackageView) {
  editingPackageId.value = row?.id ?? null
  packageForm.packageCode = row?.packageCode ?? ''
  packageForm.packageName = row?.packageName ?? ''
  packageForm.subtitle = row?.subtitle ?? ''
  packageForm.salesPrice = row?.salesPrice ?? undefined
  packageForm.originalPrice = row?.originalPrice ?? undefined
  packageForm.descriptionMd = row?.descriptionMd ?? ''
  packageForm.appKey = row?.appKey ?? ''
  packageForm.orderNo = row?.orderNo ?? 0
  packageForm.packageDesc = row?.packageDesc ?? ''
  packageForm.status = row?.status ?? '0'
}

function openPackageDialog(row?: TenantPackageView) {
  resetPackageForm(row)
  packageVisible.value = true
}

function packagePayload(): TenantPackagePayload {
  return {
    packageCode: packageForm.packageCode.trim(),
    packageName: packageForm.packageName.trim(),
    subtitle: packageForm.subtitle?.trim() || undefined,
    salesPrice: packageForm.salesPrice,
    originalPrice: packageForm.originalPrice,
    descriptionMd: packageForm.descriptionMd?.trim() || undefined,
    appKey: packageForm.appKey?.trim() || undefined,
    orderNo: packageForm.orderNo,
    packageDesc: packageForm.packageDesc?.trim() || undefined,
    status: packageForm.status,
  }
}

async function loadPackages() {
  loading.value = true
  try {
    packages.value = await queryTenantPackages()
  } finally {
    loading.value = false
  }
}

async function submitPackage() {
  await packageFormRef.value?.validate()
  const payload = packagePayload()
  if (editingPackageId.value) {
    await updateTenantPackage(editingPackageId.value, payload)
    ElMessage.success('套餐已更新')
  } else {
    await createTenantPackage(payload)
    ElMessage.success('套餐已创建')
  }
  packageVisible.value = false
  await loadPackages()
}

async function openPackageDetail(row: TenantPackageView) {
  detailPackage.value = row
  detailImpact.value = null
  packageDetailVisible.value = true
  detailLoading.value = true
  try {
    detailImpact.value = await queryTenantPackageImpact(row.id)
  } finally {
    detailLoading.value = false
  }
}

async function removePackage(row: TenantPackageView) {
  if ((row.referencedTenantCount ?? 0) > 0) {
    ElMessage.warning('该套餐仍被租户引用，不能删除')
    return
  }
  await ElMessageBox.confirm(`确认删除套餐 ${row.packageName} 吗？`, '删除确认', { type: 'warning' })
  await deleteTenantPackage(row.id)
  ElMessage.success('套餐已删除')
  await loadPackages()
}

function formatTime(value?: number | null) {
  return formatDateTime(value)
}

onMounted(loadPackages)
</script>

<style scoped>
.tenant-catalog-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-head h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.page-head p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}

.page-actions,
.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.summary-strip article {
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.summary-strip strong {
  display: block;
  font-size: 24px;
  line-height: 1.2;
}

.summary-strip span,
.muted {
  color: var(--el-text-color-secondary);
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

.drawer-section h3 {
  margin: 0;
  font-size: 16px;
}

.action-list {
  margin: 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
}

@media (max-width: 720px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-strip,
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
