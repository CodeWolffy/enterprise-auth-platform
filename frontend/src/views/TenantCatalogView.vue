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
            <el-table-column prop="packageDesc" label="套餐说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column fixed="right" label="操作" width="160">
              <template #default="{ row }">
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
            <el-table-column prop="sortOrder" label="排序" width="90" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column fixed="right" label="操作" width="160">
              <template #default="{ row }">
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
const editingPackageId = ref<number | null>(null)
const editingCapabilityId = ref<number | null>(null)
const packageFormRef = ref<FormInstance>()
const capabilityFormRef = ref<FormInstance>()
const packages = ref<TenantPackageView[]>([])
const capabilities = ref<TenantCapabilityView[]>([])

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
  await ElMessageBox.confirm(`确认删除套餐 ${row.packageName} 吗？`, '删除确认', { type: 'warning' })
  await deleteTenantPackage(row.id)
  ElMessage.success('套餐已删除')
  await loadPackages()
}

async function removeCapability(row: TenantCapabilityView) {
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
</style>
