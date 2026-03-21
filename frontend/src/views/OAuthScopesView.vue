<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Scopes</span>
        <strong>{{ scopes.length }}</strong>
        <span>平台级 OAuth2 作用域总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Enabled</span>
        <strong>{{ enabledCount }}</strong>
        <span>当前启用中的作用域</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Default</span>
        <strong>{{ defaultCount }}</strong>
        <span>默认勾选作用域</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Consent</span>
        <strong>{{ consentVisibleCount }}</strong>
        <span>在同意页展示的作用域</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">OAuth Scope Registry</span>
          <h3>OAuth2 作用域管理</h3>
        </div>
        <div class="actions">
          <el-button @click="goToClients">返回客户端管理</el-button>
          <el-button type="primary" @click="openDialog()">新增作用域</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="scopes" stripe>
        <el-table-column prop="scopeCode" label="作用域编码" min-width="180" />
        <el-table-column prop="scopeName" label="作用域名称" min-width="160" />
        <el-table-column prop="scopeType" label="作用域类型" min-width="120" />
        <el-table-column prop="scopeDesc" label="作用域说明" min-width="260" show-overflow-tooltip />
        <el-table-column label="默认选中" width="100">
          <template #default="{ row }">
            <el-tag :type="row.defaultSelected ? 'success' : 'info'">{{ row.defaultSelected ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="同意页展示" width="120">
          <template #default="{ row }">
            <el-tag :type="row.visibleInConsent ? 'success' : 'info'">{{ row.visibleInConsent ? '展示' : '隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="引用客户端" width="130">
          <template #default="{ row }">
            <el-tag type="info" effect="plain">{{ row.referencedClientCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="90" />
        <el-table-column fixed="right" label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="removeScope(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑作用域' : '新增作用域'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="作用域编码" prop="scopeCode">
              <el-input v-model="form.scopeCode" :disabled="Boolean(editingId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="作用域名称" prop="scopeName">
              <el-input v-model="form.scopeName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="作用域类型">
              <el-input v-model="form.scopeType" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序值">
              <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="作用域说明">
          <el-input v-model="form.scopeDesc" type="textarea" :rows="4" />
        </el-form-item>
        <div class="switch-row">
          <el-switch v-model="form.defaultSelected" inline-prompt active-text="默认选中" inactive-text="默认不选" />
          <el-switch v-model="form.visibleInConsent" inline-prompt active-text="同意页展示" inactive-text="同意页隐藏" />
          <el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" />
        </div>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="作用域详情" size="560px">
      <template v-if="detailItem">
        <el-descriptions :column="1" border class="drawer-section drawer-section--overview">
          <el-descriptions-item label="Scope Code">{{ detailItem.scopeCode }}</el-descriptions-item>
          <el-descriptions-item label="作用域名称">{{ detailItem.scopeName }}</el-descriptions-item>
          <el-descriptions-item label="作用域类型">{{ detailItem.scopeType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="作用域说明">{{ detailItem.scopeDesc || '未配置说明' }}</el-descriptions-item>
          <el-descriptions-item label="引用客户端数">{{ detailItem.referencedClientCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="默认选中">{{ detailItem.defaultSelected ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="同意页展示">{{ detailItem.visibleInConsent ? '展示' : '隐藏' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailItem.enabled ? '启用' : '停用' }}</el-descriptions-item>
        </el-descriptions>

        <div class="drawer-section drawer-section--guide">
          <div class="eyebrow">引用提示</div>
          <el-alert
            v-if="(detailItem.referencedClientCount || 0) > 0"
            :title="`当前作用域被 ${detailItem.referencedClientCount} 个客户端引用，删除前请先处理客户端作用域配置。`"
            type="warning"
            :closable="false"
            style="margin-top: 8px"
          />
          <el-alert
            v-else
            title="当前作用域尚未被客户端引用，可按需调整或删除。"
            type="success"
            :closable="false"
            style="margin-top: 8px"
          />
          <div v-if="detailItem.referencedClientIds?.length" class="tag-row" style="margin-top: 12px">
            <el-tag v-for="clientId in detailItem.referencedClientIds" :key="clientId" type="info" effect="plain">
              {{ clientId }}
            </el-tag>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createOauthScope, deleteOauthScope, queryOauthScopes, updateOauthScope } from '@/api/oauthScopes'
import type { OAuthScopeView } from '@/types/auth'

const router = useRouter()
const loading = ref(false)
const visible = ref(false)
const detailVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const scopes = ref<OAuthScopeView[]>([])
const detailItem = ref<OAuthScopeView | null>(null)

const form = reactive({
  scopeCode: '',
  scopeName: '',
  scopeDesc: '',
  scopeType: 'API',
  defaultSelected: false,
  visibleInConsent: true,
  sortOrder: 0,
  enabled: true,
})

const rules = reactive<FormRules>({
  scopeCode: [
    { required: true, message: '请输入作用域编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9._:-]{2,128}$/, message: '作用域编码格式不正确', trigger: 'blur' },
  ],
  scopeName: [{ required: true, message: '请输入作用域名称', trigger: 'blur' }],
})

const enabledCount = computed(() => scopes.value.filter((item) => item.enabled).length)
const defaultCount = computed(() => scopes.value.filter((item) => item.defaultSelected).length)
const consentVisibleCount = computed(() => scopes.value.filter((item) => item.visibleInConsent).length)

void load()

async function load() {
  loading.value = true
  try {
    scopes.value = await queryOauthScopes()
  } finally {
    loading.value = false
  }
}

function openDialog(row?: OAuthScopeView) {
  editingId.value = row?.id ?? null
  form.scopeCode = row?.scopeCode ?? ''
  form.scopeName = row?.scopeName ?? ''
  form.scopeDesc = row?.scopeDesc ?? ''
  form.scopeType = row?.scopeType ?? 'API'
  form.defaultSelected = row?.defaultSelected ?? false
  form.visibleInConsent = row?.visibleInConsent ?? true
  form.sortOrder = row?.sortOrder ?? 0
  form.enabled = row?.enabled ?? true
  visible.value = true
}

function openDetail(row: OAuthScopeView) {
  detailItem.value = row
  detailVisible.value = true
}

async function submit() {
  await formRef.value?.validate()
  const payload = { ...form }
  if (editingId.value) {
    await updateOauthScope(editingId.value, payload)
    ElMessage.success('作用域已更新')
  } else {
    await createOauthScope(payload)
    ElMessage.success('作用域已创建')
  }
  visible.value = false
  await load()
}

async function removeScope(row: OAuthScopeView) {
  await ElMessageBox.confirm(`确认删除作用域 ${row.scopeCode} 吗？`, '删除确认', { type: 'warning' })
  await deleteOauthScope(row.id)
  ElMessage.success('作用域已删除')
  await load()
}

function goToClients() {
  void router.push({ name: 'oauth-clients' })
}
</script>

<style scoped lang="scss">
.switch-row,
.tag-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
