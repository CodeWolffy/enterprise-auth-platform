<template>
  <div class="panel-stack">
    <section class="dashboard-panel">
      <div class="panel-head">
        <div><span class="eyebrow">OAuth2 Clients</span><h3>客户端管理</h3></div>
        <el-button type="primary" @click="openCreate">新增客户端</el-button>
      </div>

      <el-table :data="clients" stripe>
        <el-table-column prop="clientName" label="客户端名称" min-width="180" />
        <el-table-column prop="clientId" label="Client ID" min-width="180" />
        <el-table-column label="类型" min-width="120">
          <template #default="{ row }"><el-tag :type="row.publicClient ? 'warning' : 'success'">{{ row.publicClient ? '公共客户端' : '机密客户端' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="授权类型" min-width="220"><template #default="{ row }">{{ row.grantTypes.join(' / ') }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag></template></el-table-column>
        <el-table-column fixed="right" label="操作" width="420">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.id)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openConsents(row)">授权记录</el-button>
            <el-button link type="warning" @click="toggleStatus(row)">{{ row.enabled ? '禁用' : '启用' }}</el-button>
            <el-button link :disabled="row.publicClient" @click="openRotateSecret(row)">轮换密钥</el-button>
            <el-button link type="danger" @click="removeClient(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑客户端' : '新增客户端'" width="760px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="rules">
        <el-form-item label="客户端名称" prop="clientName"><el-input v-model="form.clientName" /></el-form-item>
        <el-form-item label="Client ID" prop="clientId"><el-input v-model="form.clientId" :disabled="Boolean(editingId)" /></el-form-item>
        <el-form-item label="客户端类型"><el-switch v-model="form.publicClient" inline-prompt active-text="公共" inactive-text="机密" /></el-form-item>
        <el-form-item label="客户端密钥" prop="clientSecret"><el-input v-model="form.clientSecret" :placeholder="form.publicClient ? '公共客户端无需填写' : '请输入客户端密钥'" show-password /></el-form-item>
        <el-form-item label="重定向地址"><el-input v-model="form.redirectUrisText" type="textarea" :rows="3" placeholder="每行一个地址" /></el-form-item>
        <el-form-item label="作用域">
          <el-select v-model="form.scopes" multiple style="width: 100%">
            <el-option v-for="scope in availableScopes" :key="scope.id" :label="`${scope.scopeCode}（${scope.scopeName}）`" :value="scope.scopeCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="授权类型">
          <el-select v-model="form.grantTypes" multiple style="width: 100%">
            <el-option label="authorization_code" value="authorization_code" />
            <el-option label="refresh_token" value="refresh_token" />
            <el-option label="client_credentials" value="client_credentials" :disabled="form.publicClient" />
          </el-select>
        </el-form-item>
        <el-form-item label="安全选项">
          <div class="switch-row">
            <el-switch v-model="form.requirePkce" inline-prompt active-text="启用 PKCE" inactive-text="关闭 PKCE" />
            <el-switch v-model="form.requireConsent" inline-prompt active-text="需要确认" inactive-text="免确认" />
          </div>
        </el-form-item>
        <el-form-item label="启用状态"><el-switch v-model="form.clientStatus" :active-value="1" :inactive-value="0" inline-prompt active-text="启用" inactive-text="禁用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="客户端详情" size="780px">
      <template v-if="detail">
        <el-descriptions :column="2" border class="drawer-section drawer-section--overview">
          <el-descriptions-item label="客户端名称">{{ detail.clientName }}</el-descriptions-item>
          <el-descriptions-item label="Client ID">{{ detail.clientId }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.publicClient ? '公共客户端' : '机密客户端' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.enabled ? '启用' : '禁用' }}</el-descriptions-item>
          <el-descriptions-item label="授权类型" :span="2">{{ detail.grantTypes.join('、') }}</el-descriptions-item>
          <el-descriptions-item label="重定向地址" :span="2">{{ detail.redirectUris.join('；') || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="summary-grid">
          <article class="summary-card"><strong>{{ detail.scopeDetails?.length || 0 }}</strong><span>已配置作用域</span></article>
          <article class="summary-card"><strong>{{ consentVisibleCount }}</strong><span>同意页可见</span></article>
          <article class="summary-card"><strong>{{ defaultSelectedCount }}</strong><span>默认选中</span></article>
        </div>

        <el-alert v-if="detail.integrationGuidance" :title="detail.integrationGuidance.summary" type="info" :closable="false" />

        <div class="guide-block drawer-section drawer-section--scopes">
          <div class="panel-head"><div><span class="eyebrow">Scopes</span><h3>作用域说明</h3></div></div>
          <div class="tag-row">
            <el-tag v-for="(count, type) in detail.scopeTypeSummary || {}" :key="type" type="info" effect="plain">{{ type }} × {{ count }}</el-tag>
          </div>
          <div class="scope-cards">
            <article v-for="scope in detail.scopeDetails || []" :key="scope.scopeCode" class="scope-card">
              <div class="scope-head"><strong>{{ scope.scopeName }}</strong><el-tag size="small">{{ scope.scopeCode }}</el-tag></div>
              <p>{{ scope.scopeDesc || '该作用域暂未配置详细说明。' }}</p>
              <div class="scope-meta">
                <span>类型：{{ scope.scopeType || 'default' }}</span>
                <span>{{ scope.visibleInConsent ? '同意页可见' : '同意页隐藏' }}</span>
                <span>{{ scope.defaultSelected ? '默认选中' : '按需勾选' }}</span>
              </div>
            </article>
          </div>
        </div>

        <div class="guide-block drawer-section drawer-section--guide">
          <div class="panel-head"><div><span class="eyebrow">Guide</span><h3>接入建议</h3></div><el-button type="primary" plain @click="openConsents(detail)">查看授权记录</el-button></div>
          <pre class="json-pre">{{ integrationSummary }}</pre>
          <pre class="json-pre">{{ authorizeUrlExample }}</pre>
          <pre v-if="!detail.publicClient" class="json-pre">{{ tokenCommandExample }}</pre>
        </div>

        <div class="guide-block drawer-section drawer-section--history">
          <div class="panel-head"><div><span class="eyebrow">History</span><h3>状态历史</h3></div></div>
          <el-timeline v-if="detail.statusHistory?.length">
            <el-timeline-item v-for="item in detail.statusHistory" :key="`${item.eventType}-${item.occurredAt}-${item.operator}`" :timestamp="item.occurredAt || '-'" placement="top">
              <div class="history-item"><strong>{{ item.summary }}</strong><span>操作人：{{ item.operator || '-' }}</span></div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="secretVisible" title="轮换客户端密钥" width="520px">
      <el-form label-position="top"><el-form-item label="新的客户端密钥"><el-input v-model="rotateSecretForm.clientSecret" show-password placeholder="请输入新的客户端密钥" /></el-form-item></el-form>
      <template #footer><el-button @click="secretVisible = false">取消</el-button><el-button type="primary" @click="submitRotateSecret">确认轮换</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createClient, deleteClient, queryClientDetail, queryClients, rotateClientSecret, updateClient, updateClientStatus } from '@/api/oauthClients'
import { queryOauthScopes } from '@/api/oauthScopes'
import type { ClientView, OAuthScopeView } from '@/types/auth'

const router = useRouter()
const clients = ref<ClientView[]>([])
const availableScopes = ref<OAuthScopeView[]>([])
const visible = ref(false)
const detailVisible = ref(false)
const secretVisible = ref(false)
const editingId = ref<number | null>(null)
const rotateClientId = ref<number | null>(null)
const detail = ref<ClientView | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ clientId: '', clientName: '', clientSecret: '', publicClient: true, redirectUrisText: 'http://127.0.0.1:5173/auth/callback', scopes: ['openid', 'profile', 'api.read', 'api.write'], grantTypes: ['authorization_code', 'refresh_token'], requirePkce: true, requireConsent: true, clientStatus: 1 })
const rotateSecretForm = reactive({ clientSecret: '' })
const rules = reactive<FormRules>({ clientId: [{ required: true, message: '请输入 Client ID', trigger: 'blur' }], clientName: [{ required: true, message: '请输入客户端名称', trigger: 'blur' }], clientSecret: [{ validator: (_rule, value, callback) => { if (!form.publicClient && !editingId.value && !value) { callback(new Error('机密客户端必须填写客户端密钥')); return } callback() }, trigger: 'blur' }] })
const consentVisibleCount = computed(() => (detail.value?.scopeDetails || []).filter((item) => item.visibleInConsent).length)
const defaultSelectedCount = computed(() => (detail.value?.scopeDetails || []).filter((item) => item.defaultSelected).length)
const integrationSummary = computed(() => detail.value ? JSON.stringify({ clientId: detail.value.clientId, recommendedGrantType: detail.value.integrationGuidance?.recommendedGrantType, scopes: detail.value.scopes, grantTypes: detail.value.grantTypes, redirectUris: detail.value.redirectUris, scopeTips: detail.value.integrationGuidance?.scopeTips || [] }, null, 2) : '')
const authorizeUrlExample = computed(() => !detail.value ? '' : `${window.location.origin}/oauth2/authorize?response_type=code&client_id=${detail.value.clientId}&redirect_uri=${encodeURIComponent(detail.value.redirectUris[0] || 'http://127.0.0.1:5173/auth/callback')}&scope=${encodeURIComponent(detail.value.scopes.join(' '))}&state=demo-state`)
const tokenCommandExample = computed(() => !detail.value ? '' : ['curl --request POST \\', `  --url ${window.location.origin}/oauth2/token \\`, "  --header 'Content-Type: application/x-www-form-urlencoded' \\", `  --data 'grant_type=client_credentials&client_id=${detail.value.clientId}&client_secret=请替换为实际密钥&scope=${detail.value.scopes.join(' ')}'`].join('\n'))
void load()
async function load() { const [clientList, scopes] = await Promise.all([queryClients(), queryOauthScopes()]); clients.value = clientList; availableScopes.value = scopes.filter((item) => item.enabled) }
function resetForm() { Object.assign(form, { clientId: '', clientName: '', clientSecret: '', publicClient: true, redirectUrisText: 'http://127.0.0.1:5173/auth/callback', scopes: ['openid', 'profile', 'api.read', 'api.write'], grantTypes: ['authorization_code', 'refresh_token'], requirePkce: true, requireConsent: true, clientStatus: 1 }) }
function openCreate() { editingId.value = null; resetForm(); visible.value = true }
function openEdit(client: ClientView) { editingId.value = client.id; Object.assign(form, { clientId: client.clientId, clientName: client.clientName, clientSecret: '', publicClient: client.publicClient, redirectUrisText: client.redirectUris.join('\n'), scopes: [...client.scopes], grantTypes: [...client.grantTypes], requirePkce: client.requirePkce, requireConsent: client.requireConsent, clientStatus: client.enabled ? 1 : 0 }); visible.value = true }
async function viewDetail(id: number) { detail.value = await queryClientDetail(id); detailVisible.value = true }
function openRotateSecret(client: ClientView) { rotateClientId.value = client.id; rotateSecretForm.clientSecret = ''; secretVisible.value = true }
function openConsents(client: ClientView) { void router.push({ name: 'consents', query: { clientId: client.clientId } }) }
async function submit() { if (!formRef.value) return; await formRef.value.validate(); const payload = { clientId: form.clientId, clientName: form.clientName, clientSecret: form.clientSecret, publicClient: form.publicClient, redirectUris: form.redirectUrisText.split('\n').map((item) => item.trim()).filter(Boolean), scopes: form.scopes, grantTypes: form.grantTypes, requirePkce: form.requirePkce, requireConsent: form.requireConsent, clientStatus: form.clientStatus }; const result = editingId.value ? await updateClient(editingId.value, payload) : await createClient(payload); visible.value = false; ElMessage.success(editingId.value ? '客户端已更新' : '客户端已创建'); if (result.issuedClientSecret) { await ElMessageBox.alert(`客户端密钥：${result.issuedClientSecret}`, '请妥善保存密钥', { type: 'success' }) } await load() }
async function submitRotateSecret() { if (!rotateClientId.value) return; const updated = await rotateClientSecret(rotateClientId.value, { clientSecret: rotateSecretForm.clientSecret }); secretVisible.value = false; ElMessage.success('客户端密钥已轮换'); if (updated.issuedClientSecret) { await ElMessageBox.alert(`新的客户端密钥：${updated.issuedClientSecret}`, '请妥善保存密钥', { type: 'success' }) } await load() }
async function toggleStatus(client: ClientView) { const nextEnabled = !client.enabled; await updateClientStatus(client.id, { enabled: nextEnabled }); ElMessage.success(`客户端已${nextEnabled ? '启用' : '禁用'}`); await load() }
async function removeClient(client: ClientView) { await ElMessageBox.confirm(`确定要删除客户端 ${client.clientName}（${client.clientId}）吗？`, '删除确认', { type: 'warning' }); await deleteClient(client.id); ElMessage.success('客户端已删除'); await load() }
</script>

<style scoped lang="scss">
.switch-row,.tag-row{display:flex;gap:12px;flex-wrap:wrap}
.guide-block{margin-top:24px;display:grid;gap:16px}
.summary-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin-top:16px}
.summary-card{display:grid;gap:6px;padding:14px 16px;border-radius:14px;background:rgba(241,245,249,.92);color:#475569}
.summary-card strong{font-size:24px;color:#0f172a}
.scope-cards{display:grid;gap:12px}
.scope-card{padding:16px;border:1px solid rgba(15,23,42,.08);border-radius:16px;background:rgba(248,250,252,.9);display:grid;gap:10px}
.scope-head{display:flex;justify-content:space-between;gap:12px;align-items:center}
.scope-meta{display:flex;gap:12px;flex-wrap:wrap;color:var(--el-text-color-secondary);font-size:12px}
.json-pre{margin:0;white-space:pre-wrap;word-break:break-all}
.history-item{display:grid;gap:4px}
</style>
