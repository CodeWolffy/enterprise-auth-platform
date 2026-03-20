<template>
  <div class="panel-stack">
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
              {{ row.publicClient ? '公共客户端' : '私有客户端' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="授权类型" min-width="220">
          <template #default="{ row }">{{ row.grantTypes.join(' / ') }}</template>
        </el-table-column>
        <el-table-column label="授权确认" min-width="110">
          <template #default="{ row }">{{ row.requireConsent ? '需要' : '跳过' }}</template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="180">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeClient(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑客户端' : '新增客户端'" width="720px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户端名称">
              <el-input v-model="form.clientName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Client ID">
              <el-input v-model="form.clientId" :disabled="Boolean(editingId)" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="客户端类型">
          <el-switch v-model="form.publicClient" inline-prompt active-text="公共" inactive-text="私有" />
        </el-form-item>
        <el-form-item label="客户端密钥">
          <el-input v-model="form.clientSecret" :placeholder="form.publicClient ? '公共客户端无需填写' : '请输入客户端密钥'" />
        </el-form-item>
        <el-form-item label="重定向地址">
          <el-input v-model="form.redirectUrisText" type="textarea" :rows="3" placeholder="每行一个地址" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="作用域">
              <el-select v-model="form.scopes" multiple>
                <el-option label="openid" value="openid" />
                <el-option label="profile" value="profile" />
                <el-option label="api.read" value="api.read" />
                <el-option label="api.write" value="api.write" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="授权类型">
              <el-select v-model="form.grantTypes" multiple>
                <el-option label="authorization_code" value="authorization_code" />
                <el-option label="refresh_token" value="refresh_token" />
                <el-option label="client_credentials" value="client_credentials" :disabled="form.publicClient" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="安全选项">
          <div class="switch-row">
            <el-switch v-model="form.requirePkce" inline-prompt active-text="PKCE" inactive-text="PKCE" />
            <el-switch v-model="form.requireConsent" inline-prompt active-text="需确认" inactive-text="免确认" />
          </div>
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
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createClient, deleteClient, queryClients, updateClient } from '@/api/oauthClients'
import type { ClientView } from '@/types/auth'

const clients = ref<ClientView[]>([])
const visible = ref(false)
const editingId = ref<number | null>(null)
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
})

void load()

async function load() {
  clients.value = await queryClients()
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
  })
  visible.value = true
}

async function submit() {
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
  }
  if (editingId.value) {
    await updateClient(editingId.value, payload)
    ElMessage.success('客户端已更新')
  } else {
    const created = await createClient(payload)
    ElMessage.success(created.issuedClientSecret ? '客户端已创建，已返回明文密钥' : '公共客户端已创建')
  }
  visible.value = false
  await load()
}

async function removeClient(id: number) {
  await ElMessageBox.confirm('删除后该客户端将无法继续发起授权，是否继续？', '删除确认', {
    type: 'warning',
  })
  await deleteClient(id)
  ElMessage.success('客户端已删除')
  await load()
}
</script>
