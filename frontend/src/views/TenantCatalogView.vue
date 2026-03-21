<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Packages</span>
        <strong>{{ packages.length }}</strong>
        <span>当前定义的租户套餐</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Capabilities</span>
        <strong>{{ capabilities.length }}</strong>
        <span>当前定义的租户能力</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Enabled Packages</span>
        <strong>{{ enabledPackageCount }}</strong>
        <span>启用中的套餐数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Enabled Capabilities</span>
        <strong>{{ enabledCapabilityCount }}</strong>
        <span>启用中的能力数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Tenant Catalog</span>
          <h3>租户套餐与能力</h3>
        </div>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="套餐管理" name="packages">
          <div class="panel-head sub-head">
            <div>
              <span class="eyebrow">Packages</span>
              <h4>套餐定义</h4>
            </div>
            <el-button type="primary" @click="openPackageDialog()">新增套餐</el-button>
          </div>
          <el-table v-loading="loadingPackages" :data="packages" stripe>
            <el-table-column prop="packageCode" label="套餐编码" min-width="160" />
            <el-table-column prop="packageName" label="套餐名称" min-width="180" />
            <el-table-column prop="userQuota" label="用户配额" width="110" />
            <el-table-column prop="storageQuotaGb" label="存储配额(GB)" width="130" />
            <el-table-column label="能力集合" min-width="240">
              <template #default="{ row }">
                <el-tag v-for="code in row.capabilityCodes" :key="code" class="scope-tag" size="small">{{ code }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="引用租户" width="110">
              <template #default="{ row }">
                <el-tag type="info" effect="plain">{{ row.referencedTenantCount || 0 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="packageDesc" label="套餐说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column fixed="right" label="操作" width="220">
              <template #default="{ row }">
                <el-button link type="primary" @click="openPackageDetail(row)">详情</el-button>
                <el-button link type="primary" @click="openPackageDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="removePackage(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="能力管理" name="capabilities">
          <div class="panel-head sub-head">
            <div>
              <span class="eyebrow">Capabilities</span>
              <h4>能力定义</h4>
            </div>
            <el-button type="primary" @click="openCapabilityDialog()">新增能力</el-button>
          </div>
          <el-table v-loading="loadingCapabilities" :data="capabilities" stripe>
            <el-table-column prop="capabilityCode" label="能力编码" min-width="180" />
            <el-table-column prop="capabilityName" label="能力名称" min-width="180" />
            <el-table-column prop="capabilityDesc" label="能力说明" min-width="260" show-overflow-tooltip />
            <el-table-column label="引用套餐" width="110">
              <template #default="{ row }">
                <el-tag type="info" effect="plain">{{ row.referencedPackageCount || 0 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="排序" width="90" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column fixed="right" label="操作" width="220">
              <template #default="{ row }">
                <el-button link type="primary" @click="openCapabilityDetail(row)">详情</el-button>
                <el-button link type="primary" @click="openCapabilityDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="removeCapability(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="packageVisible" :title="editingPackageId ? '编辑套餐' : '新增套餐'" width="720px">
      <el-form ref="packageFormRef" :model="packageForm" :rules="packageRules" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="套餐编码" prop="packageCode">
              <el-input v-model="packageForm.packageCode" :disabled="Boolean(editingPackageId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="套餐名称" prop="packageName">
              <el-input v-model="packageForm.packageName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户配额">
              <el-input-number v-model="packageForm.userQuota" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存储配额(GB)">
              <el-input-number v-model="packageForm.storageQuotaGb" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="套餐说明">
          <el-input v-model="packageForm.packageDesc" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="能力集合" prop="capabilityCodes">
          <el-select v-model="packageForm.capabilityCodes" multiple style="width: 100%">
            <el-option
              v-for="item in capabilities"
              :key="item.capabilityCode"
              :label="`${item.capabilityCode} (${item.capabilityName})`"
              :value="item.capabilityCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="packageForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="packageVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPackage">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="capabilityVisible" :title="editingCapabilityId ? '编辑能力' : '新增能力'" width="640px">
      <el-form ref="capabilityFormRef" :model="capabilityForm" :rules="capabilityRules" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="能力编码" prop="capabilityCode">
              <el-input v-model="capabilityForm.capabilityCode" :disabled="Boolean(editingCapabilityId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="能力名称" prop="capabilityName">
              <el-input v-model="capabilityForm.capabilityName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序值">
              <el-input-number v-model="capabilityForm.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用">
              <el-switch v-model="capabilityForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="能力说明">
          <el-input v-model="capabilityForm.capabilityDesc" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="capabilityVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCapability">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="packageDetailVisible" title="套餐详情" size="560px">
      <template v-if="detailPackage">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="套餐编码">{{ detailPackage.packageCode }}</el-descriptions-item>
          <el-descriptions-item label="套餐名称">{{ detailPackage.packageName }}</el-descriptions-item>
          <el-descriptions-item label="用户配额">{{ detailPackage.userQuota ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="存储配额(GB)">{{ detailPackage.storageQuotaGb ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="套餐说明">{{ detailPackage.packageDesc || '未配置说明' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailPackage.enabled ? '启用' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="引用租户数">{{ detailPackage.referencedTenantCount || 0 }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-block">
          <div class="eyebrow">能力清单</div>
          <div class="tag-wrap">
            <el-tag v-for="code in detailPackage.capabilityCodes" :key="code" type="info" effect="plain">{{ code }}</el-tag>
          </div>
        </div>

        <div class="detail-block">
          <div class="eyebrow">引用提示</div>
          <el-alert
            v-if="(detailPackage.referencedTenantCount || 0) > 0"
            :title="`当前套餐被 ${detailPackage.referencedTenantCount} 个租户使用，删除或变更前请先评估租户迁移。`"
            type="warning"
            :closable="false"
            style="margin-top: 8px"
          />
          <el-alert
            v-else
            title="当前套餐尚未被租户引用，可按需调整。"
            type="success"
            :closable="false"
            style="margin-top: 8px"
          />
          <div v-if="detailPackage.referencedTenantIds?.length" class="tag-wrap" style="margin-top: 10px">
            <el-tag v-for="tenantId in detailPackage.referencedTenantIds" :key="tenantId">{{ tenantId }}</el-tag>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="capabilityDetailVisible" title="能力详情" size="560px">
      <template v-if="detailCapability">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="能力编码">{{ detailCapability.capabilityCode }}</el-descriptions-item>
          <el-descriptions-item label="能力名称">{{ detailCapability.capabilityName }}</el-descriptions-item>
          <el-descriptions-item label="能力说明">{{ detailCapability.capabilityDesc || '未配置说明' }}</el-descriptions-item>
          <el-descriptions-item label="排序值">{{ detailCapability.sortOrder ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailCapability.enabled ? '启用' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="引用套餐数">{{ detailCapability.referencedPackageCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="覆盖租户数">{{ detailCapability.referencedTenantCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="覆盖记录数">{{ detailCapability.overrideReferenceCount || 0 }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-block">
          <div class="eyebrow">引用提示</div>
          <el-alert
            v-if="(detailCapability.referencedPackageCount || 0) > 0 || (detailCapability.overrideReferenceCount || 0) > 0"
            title="该能力存在引用关系，删除前请先解除套餐绑定并清理租户覆盖配置。"
            type="warning"
            :closable="false"
            style="margin-top: 8px"
          />
          <el-alert
            v-else
            title="当前能力无引用关系，可按需调整。"
            type="success"
            :closable="false"
            style="margin-top: 8px"
          />
          <div v-if="detailCapability.referencedPackageCodes?.length" class="tag-wrap" style="margin-top: 10px">
            <el-tag v-for="pkg in detailCapability.referencedPackageCodes" :key="pkg" type="info" effect="plain">套餐: {{ pkg }}</el-tag>
          </div>
          <div v-if="detailCapability.referencedTenantIds?.length" class="tag-wrap" style="margin-top: 10px">
            <el-tag v-for="tenantId in detailCapability.referencedTenantIds" :key="tenantId">租户: {{ tenantId }}</el-tag>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createTenantCapability,
  createTenantPackage,
  deleteTenantCapability,
  deleteTenantPackage,
  queryTenantCapabilities,
  queryTenantPackages,
  updateTenantCapability,
  updateTenantPackage,
} from '@/api/tenantCatalog'
import type { TenantCapabilityView, TenantPackageView } from '@/types/auth'

const activeTab = ref<'packages' | 'capabilities'>('packages')
const loadingPackages = ref(false)
const loadingCapabilities = ref(false)
const packageVisible = ref(false)
const capabilityVisible = ref(false)
const packageDetailVisible = ref(false)
const capabilityDetailVisible = ref(false)
const editingPackageId = ref<number | null>(null)
const editingCapabilityId = ref<number | null>(null)
const packageFormRef = ref<FormInstance>()
const capabilityFormRef = ref<FormInstance>()
const packages = ref<TenantPackageView[]>([])
const capabilities = ref<TenantCapabilityView[]>([])
const detailPackage = ref<TenantPackageView | null>(null)
const detailCapability = ref<TenantCapabilityView | null>(null)

const packageForm = reactive({
  packageCode: '',
  packageName: '',
  userQuota: 0,
  storageQuotaGb: 0,
  packageDesc: '',
  enabled: true,
  capabilityCodes: [] as string[],
})

const capabilityForm = reactive({
  capabilityCode: '',
  capabilityName: '',
  capabilityDesc: '',
  sortOrder: 0,
  enabled: true,
})

const packageRules = reactive<FormRules>({
  packageCode: [
    { required: true, message: '请输入套餐编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]{2,64}$/, message: '套餐编码格式不正确', trigger: 'blur' },
  ],
  packageName: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  capabilityCodes: [{ required: true, message: '请至少选择一个能力', trigger: 'change' }],
})

const capabilityRules = reactive<FormRules>({
  capabilityCode: [
    { required: true, message: '请输入能力编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]{2,64}$/, message: '能力编码格式不正确', trigger: 'blur' },
  ],
  capabilityName: [{ required: true, message: '请输入能力名称', trigger: 'blur' }],
})

const enabledPackageCount = computed(() => packages.value.filter((item) => item.enabled).length)
const enabledCapabilityCount = computed(() => capabilities.value.filter((item) => item.enabled).length)

void Promise.all([loadPackages(), loadCapabilities()])

async function loadPackages() {
  loadingPackages.value = true
  try {
    packages.value = await queryTenantPackages()
  } finally {
    loadingPackages.value = false
  }
}

async function loadCapabilities() {
  loadingCapabilities.value = true
  try {
    capabilities.value = await queryTenantCapabilities()
  } finally {
    loadingCapabilities.value = false
  }
}

function openPackageDialog(row?: TenantPackageView) {
  editingPackageId.value = row?.id ?? null
  packageForm.packageCode = row?.packageCode ?? ''
  packageForm.packageName = row?.packageName ?? ''
  packageForm.userQuota = row?.userQuota ?? 0
  packageForm.storageQuotaGb = row?.storageQuotaGb ?? 0
  packageForm.packageDesc = row?.packageDesc ?? ''
  packageForm.enabled = row?.enabled ?? true
  packageForm.capabilityCodes = [...(row?.capabilityCodes ?? [])]
  packageVisible.value = true
}

function openCapabilityDialog(row?: TenantCapabilityView) {
  editingCapabilityId.value = row?.id ?? null
  capabilityForm.capabilityCode = row?.capabilityCode ?? ''
  capabilityForm.capabilityName = row?.capabilityName ?? ''
  capabilityForm.capabilityDesc = row?.capabilityDesc ?? ''
  capabilityForm.sortOrder = row?.sortOrder ?? 0
  capabilityForm.enabled = row?.enabled ?? true
  capabilityVisible.value = true
}

function openPackageDetail(row: TenantPackageView) {
  detailPackage.value = row
  packageDetailVisible.value = true
}

function openCapabilityDetail(row: TenantCapabilityView) {
  detailCapability.value = row
  capabilityDetailVisible.value = true
}

async function submitPackage() {
  await packageFormRef.value?.validate()
  const payload = { ...packageForm }
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

async function submitCapability() {
  await capabilityFormRef.value?.validate()
  const payload = { ...capabilityForm }
  if (editingCapabilityId.value) {
    await updateTenantCapability(editingCapabilityId.value, payload)
    ElMessage.success('能力已更新')
  } else {
    await createTenantCapability(payload)
    ElMessage.success('能力已创建')
  }
  capabilityVisible.value = false
  await Promise.all([loadCapabilities(), loadPackages()])
}

async function removePackage(row: TenantPackageView) {
  if ((row.referencedTenantCount || 0) > 0) {
    ElMessage.warning(`套餐仍被 ${row.referencedTenantCount} 个租户使用，请先迁移租户后再删除`)
    return
  }
  await ElMessageBox.confirm(`确认删除套餐 ${row.packageName} 吗？`, '删除确认', { type: 'warning' })
  await deleteTenantPackage(row.id)
  ElMessage.success('套餐已删除')
  await loadPackages()
}

async function removeCapability(row: TenantCapabilityView) {
  if ((row.referencedPackageCount || 0) > 0 || (row.overrideReferenceCount || 0) > 0) {
    ElMessage.warning('能力仍存在套餐或租户覆盖引用，请先解除引用后再删除')
    return
  }
  await ElMessageBox.confirm(`确认删除能力 ${row.capabilityName} 吗？`, '删除确认', { type: 'warning' })
  await deleteTenantCapability(row.id)
  ElMessage.success('能力已删除')
  await Promise.all([loadCapabilities(), loadPackages()])
}
</script>

<style scoped lang="scss">
.sub-head {
  margin-bottom: 16px;
}

.scope-tag {
  margin-right: 6px;
  margin-bottom: 6px;
}

.detail-block {
  margin-top: 16px;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
