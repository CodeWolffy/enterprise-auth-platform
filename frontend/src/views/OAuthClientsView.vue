<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">Clients</span>
        <strong>{{ clients.length }}</strong>
        <span>当前租户下的客户端总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Public</span>
        <strong>{{ publicClientCount }}</strong>
        <span>公共客户端数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Confidential</span>
        <strong>{{ confidentialClientCount }}</strong>
        <span>机密客户端数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">Enabled</span>
        <strong>{{ enabledClientCount }}</strong>
        <span>当前启用中的客户端</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">OAuth2 Clients</span>
          <h3>客户端管理</h3>
        </div>
        <el-button type="primary" @click="openCreate">新增客户端</el-button>
      </div>

      <el-table :data="clients" stripe>
        <el-table-column prop="clientName" label="客户端名称" min-width="180" />
        <el-table-column prop="clientId" label="Client ID" min-width="180" />
        <el-table-column label="客户端类型" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.publicClient ? 'warning' : 'success'">
              {{ row.publicClient ? '公共客户端' : '机密客户端' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="授权类型" min-width="220">
          <template #default="{ row }">{{ row.grantTypes.join(' / ') }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="授权确认" min-width="110">
          <template #default="{ row }">{{ row.requireConsent ? '需要' : '跳过' }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="420">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.id)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openConsents(row)">授权记录</el-button>
            <el-button link type="warning" @click="toggleStatus(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button link :disabled="row.publicClient" @click="openRotateSecret(row)">轮换密钥</el-button>
            <el-button link type="danger" @click="removeClient(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑客户端' : '新增客户端'" width="720px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户端名称" prop="clientName">
              <el-input v-model="form.clientName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Client ID" prop="clientId">
              <el-input v-model="form.clientId" :disabled="Boolean(editingId)" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="客户端类型">
          <el-switch v-model="form.publicClient" inline-prompt active-text="公共" inactive-text="机密" />
        </el-form-item>
        <el-form-item label="客户端密钥" prop="clientSecret">
          <el-input
            v-model="form.clientSecret"
            :placeholder="form.publicClient ? '公共客户端无需填写' : '请输入客户端密钥'"
            show-password
          />
        </el-form-item>
        <el-form-item label="重定向地址">
          <el-input v-model="form.redirectUrisText" type="textarea" :rows="3" placeholder="每行一个地址" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="作用域">
              <el-select v-model="form.scopes" multiple style="width: 100%">
                <el-option
                  v-for="scope in availableScopes"
                  :key="scope.dictCode"
                  :label="`${scope.dictCode} (${scope.dictValue})`"
                  :value="scope.dictCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授权类型">
              <el-select v-model="form.grantTypes" multiple style="width: 100%">
                <el-option label="authorization_code" value="authorization_code" />
                <el-option label="refresh_token" value="refresh_token" />
                <el-option label="client_credentials" value="client_credentials" :disabled="form.publicClient" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="安全选项">
          <div class="switch-row">
            <el-switch v-model="form.requirePkce" inline-prompt active-text="启用 PKCE" inactive-text="关闭 PKCE" />
            <el-switch
              v-model="form.requireConsent"
              inline-prompt
              active-text="需要确认"
              inactive-text="免确认"
            />
          </div>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch
            v-model="form.clientStatus"
            :active-value="1"
            :inactive-value="0"
            inline-prompt
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="客户端详情" size="760px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="客户端名称">{{ detail.clientName }}</el-descriptions-item>
          <el-descriptions-item label="Client ID">{{ detail.clientId }}</el-descriptions-item>
          <el-descriptions-item label="客户端类型">
            {{ detail.publicClient ? '公共客户端' : '机密客户端' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.enabled ? '启用' : '禁用' }}</el-descriptions-item>
          <el-descriptions-item label="PKCE">{{ detail.requirePkce ? '启用' : '关闭' }}</el-descriptions-item>
          <el-descriptions-item label="授权确认">{{ detail.requireConsent ? '需要' : '跳过' }}</el-descriptions-item>
          <el-descriptions-item label="作用域" :span="2">{{ detail.scopes.join(', ') }}</el-descriptions-item>
          <el-descriptions-item label="授权类型" :span="2">{{ detail.grantTypes.join(', ') }}</el-descriptions-item>
          <el-descriptions-item label="重定向地址" :span="2">{{ detail.redirectUris.join('；') }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="guide-block">
          <div class="panel-head">
            <div>
              <span class="eyebrow">Guide</span>
              <h3>客户端接入说明</h3>
            </div>
            <el-button type="primary" plain @click="openConsents(detail)">查看授权记录</el-button>
          </div>
          <el-alert
            :title="detail.publicClient ? '当前为公共客户端，推荐使用 Authorization Code + PKCE。' : '当前为机密客户端，请在服务端安全保存客户端密钥。'"
            type="info"
            :closable="false"
          />
          <el-tabs class="guide-tabs">
            <el-tab-pane label="接入概要">
              <pre class="json-pre">{{ integrationSummary }}</pre>
            </el-tab-pane>
            <el-tab-pane label="授权地址示例">
              <pre class="json-pre">{{ authorizeUrlExample }}</pre>
            </el-tab-pane>
            <el-tab-pane v-if="!detail.publicClient" label="Token 调用示例">
              <pre class="json-pre">{{ tokenCommandExample }}</pre>
            </el-tab-pane>
          </el-tabs>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="secretVisible" title="轮换客户端密钥" width="520px">
      <el-form label-position="top">
        <el-form-item label="新的客户端密钥">
          <el-input v-model="rotateSecretForm.clientSecret" show-password placeholder="请输入新的客户端密钥" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="secretVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRotateSecret">确认轮换</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createClient,
  deleteClient,
  queryClientDetail,
  queryClients,
  rotateClientSecret,
  updateClient,
  updateClientStatus,
} from '@/api/oauthClients'
import { queryDicts } from '@/api/system'
import type { ClientView, DictView } from '@/types/auth'

const router = useRouter()
const clients = ref<ClientView[]>([])
const availableScopes = ref<DictView[]>([])
const visible = ref(false)
const detailVisible = ref(false)
const secretVisible = ref(false)
const editingId = ref<number | null>(null)
const rotateClientId = ref<number | null>(null)
const detail = ref<ClientView | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  clientId: '',
  clientName: '',
  clientSecret: '',
  publicClient: true,
  redirectUrisText: 'http://127.0.0.1:5173/auth/callback',
  scopes: ['openid', 'profile', 'api.read', 'api.write'],
  grantTypes: ['authorization_code', 'refresh_token'],
  requirePkce: true,
  requireConsent: true,
  clientStatus: 1,
})

const rules = reactive<FormRules>({
  clientId: [{ required: true, message: '请输入 Client ID', trigger: 'blur' }],
  clientName: [{ required: true, message: '请输入客户端名称', trigger: 'blur' }],
  clientSecret: [
    {
      validator: (_rule, value, callback) => {
        if (!form.publicClient && !editingId.value && !value) {
          callback(new Error('机密客户端必须填写客户端密钥'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
})

const rotateSecretForm = reactive({
  clientSecret: '',
})

const publicClientCount = computed(() => clients.value.filter((item) => item.publicClient).length)
const confidentialClientCount = computed(() => clients.value.filter((item) => !item.publicClient).length)
const enabledClientCount = computed(() => clients.value.filter((item) => item.enabled).length)

const integrationSummary = computed(() => {
  if (!detail.value) {
    return ''
  }
  return JSON.stringify(
    {
      clientId: detail.value.clientId,
      clientType: detail.value.publicClient ? 'public' : 'confidential',
      grantTypes: detail.value.grantTypes,
      scopes: detail.value.scopes,
      redirectUris: detail.value.redirectUris,
      requirePkce: detail.value.requirePkce,
      requireConsent: detail.value.requireConsent,
      tokenEndpoint: `${window.location.origin}/oauth2/token`,
      authorizationEndpoint: `${window.location.origin}/oauth2/authorize`,
    },
    null,
    2,
  )
})

const authorizeUrlExample = computed(() => {
  if (!detail.value) {
    return ''
  }
  const redirectUri = encodeURIComponent(detail.value.redirectUris[0] || 'http://127.0.0.1:5173/auth/callback')
  const scopes = encodeURIComponent(detail.value.scopes.join(' '))
  return `${window.location.origin}/oauth2/authorize?response_type=code&client_id=${detail.value.clientId}&redirect_uri=${redirectUri}&scope=${scopes}&state=demo-state`
})

const tokenCommandExample = computed(() => {
  if (!detail.value) {
    return ''
  }
  return [
    'curl --request POST \\',
    `  --url ${window.location.origin}/oauth2/token \\`,
    "  --header 'Content-Type: application/x-www-form-urlencoded' \\",
    `  --data 'grant_type=client_credentials&client_id=${detail.value.clientId}&client_secret=请替换为实际密钥&scope=${detail.value.scopes.join(' ')}'`,
  ].join('\n')
})

void load()

async function load() {
  const [clientList, dictPage] = await Promise.all([queryClients(), queryDicts({ page: 1, size: 200 })])
  clients.value = clientList
  availableScopes.value = dictPage.records.filter((item) => item.dictType === 'oauth_scope')
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    clientId: '',
    clientName: '',
    clientSecret: '',
    publicClient: true,
    redirectUrisText: 'http://127.0.0.1:5173/auth/callback',
    scopes: ['openid', 'profile', 'api.read', 'api.write'],
    grantTypes: ['authorization_code', 'refresh_token'],
    requirePkce: true,
    requireConsent: true,
    clientStatus: 1,
  })
  visible.value = true
}

function openEdit(client: ClientView) {
  editingId.value = client.id
  Object.assign(form, {
    clientId: client.clientId,
    clientName: client.clientName,
    clientSecret: '',
    publicClient: client.publicClient,
    redirectUrisText: client.redirectUris.join('\n'),
    scopes: [...client.scopes],
    grantTypes: [...client.grantTypes],
    requirePkce: client.requirePkce,
    requireConsent: client.requireConsent,
    clientStatus: client.enabled ? 1 : 0,
  })
  visible.value = true
}

async function viewDetail(id: number) {
  detail.value = await queryClientDetail(id)
  detailVisible.value = true
}

function openRotateSecret(client: ClientView) {
  rotateClientId.value = client.id
  rotateSecretForm.clientSecret = ''
  secretVisible.value = true
}

function openConsents(client: ClientView) {
  void router.push({ name: 'consents', query: { clientId: client.clientId } })
}

async function submit() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()

  const payload = {
    clientId: form.clientId,
    clientName: form.clientName,
    clientSecret: form.clientSecret,
    publicClient: form.publicClient,
    redirectUris: form.redirectUrisText.split('\n').map((item) => item.trim()).filter(Boolean),
    scopes: form.scopes,
    grantTypes: form.grantTypes,
    requirePkce: form.requirePkce,
    requireConsent: form.requireConsent,
    clientStatus: form.clientStatus,
  }

  if (editingId.value) {
    const updated = await updateClient(editingId.value, payload)
    ElMessage.success('客户端已更新')
    if (updated.issuedClientSecret) {
      await ElMessageBox.alert(`新的客户端密钥：${updated.issuedClientSecret}`, '密钥已更新', { type: 'success' })
    }
  } else {
    const created = await createClient(payload)
    ElMessage.success(created.issuedClientSecret ? '客户端已创建，并已返回明文密钥' : '公共客户端已创建')
    if (created.issuedClientSecret) {
      await ElMessageBox.alert(`客户端密钥：${created.issuedClientSecret}`, '请妥善保存密钥', { type: 'success' })
    }
  }
  visible.value = false
  await load()
}

async function submitRotateSecret() {
  if (!rotateClientId.value) {
    return
  }
  const updated = await rotateClientSecret(rotateClientId.value, {
    clientSecret: rotateSecretForm.clientSecret,
  })
  secretVisible.value = false
  ElMessage.success('客户端密钥已轮换')
  if (updated.issuedClientSecret) {
    await ElMessageBox.alert(`新的客户端密钥：${updated.issuedClientSecret}`, '请妥善保存密钥', { type: 'success' })
  }
  await load()
}

async function toggleStatus(client: ClientView) {
  const nextEnabled = !client.enabled
  await updateClientStatus(client.id, { enabled: nextEnabled })
  ElMessage.success(`客户端已${nextEnabled ? '启用' : '禁用'}`)
  await load()
}

async function removeClient(client: ClientView) {
  await ElMessageBox.confirm(
    `确定要删除客户端 ${client.clientName}（${client.clientId}）吗？删除后客户端将无法继续发起授权。`,
    '删除确认',
    { type: 'warning' },
  )
  await deleteClient(client.id)
  ElMessage.success('客户端已删除')
  if (detail.value?.id === client.id) {
    detailVisible.value = false
    detail.value = null
  }
  await load()
}
</script>

<style scoped lang="scss">
.guide-block {
  margin-top: 24px;
  display: grid;
  gap: 16px;
}

.guide-tabs {
  margin-top: 8px;
}
</style>
