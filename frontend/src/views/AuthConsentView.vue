<template>
  <div class="consent-stage">
    <section class="consent-panel">
      <span class="eyebrow">OAuth2 Consent</span>
      <h1>确认客户端访问权限</h1>
      <p class="consent-summary">
        客户端
        <strong>{{ clientDisplay }}</strong>
        正在申请访问租户
        <strong>{{ tenantDisplay }}</strong>
        的资源，请确认授权范围。
      </p>

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

      <div v-if="loading" class="consent-loading">
        <el-skeleton :rows="4" animated />
      </div>

      <template v-else>
        <div class="scope-list" v-if="scopes.length > 0">
          <label v-for="scope in scopes" :key="scope.code" class="scope-item">
            <el-checkbox v-model="selectedScopes" :value="scope.code">
              <span class="scope-name">{{ scope.code }}</span>
            </el-checkbox>
            <p>{{ scope.description }}</p>
          </label>
        </div>
        <el-empty v-else description="未检测到可授权作用域" />

        <div class="consent-actions">
          <el-button type="primary" :loading="submitting" @click="submitConsent('approve')">确认授权</el-button>
          <el-button :loading="submitting" @click="submitConsent('deny')">拒绝</el-button>
        </div>
      </template>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { ApiResponse, CsrfTokenResponse } from '@/types/auth'
import { getBackendOrigin } from '@/utils/oauth'

type ConsentScope = {
  code: string
  description: string
}

type ConsentContextResponse = {
  clientId: string
  clientName: string
  tenantId: string
  tenantName: string
  tenantLevel: string
  clientMode: string
  state: string
  scopes: Array<{ scopeCode: string; scopeDesc: string }>
  csrf: CsrfTokenResponse
}

const backendOrigin = getBackendOrigin()
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const csrfParamName = ref('')
const csrfValue = ref('')
const scopes = ref<ConsentScope[]>([])
const selectedScopes = ref<string[]>([])

const params = new URLSearchParams(window.location.search)
const clientId = params.get('client_id') ?? ''
const state = params.get('state') ?? ''
const tenantId = params.get('tenantId') ?? 'platform'

const clientDisplay = computed(() => clientId || '未指定客户端')
const tenantDisplay = computed(() => tenantId || 'platform')

onMounted(async () => {
  await loadConsentContext()
})

async function loadConsentContext() {
  loading.value = true
  errorMessage.value = ''
  try {
    const consentUrl = buildBackendConsentContextUrl()
    const response = await fetch(consentUrl, {
      method: 'GET',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
      },
    })

    if (!response.ok) {
      throw new Error(`无法加载授权上下文（${response.status}）`)
    }

    const payload = (await response.json()) as ApiResponse<ConsentContextResponse>
    const context = payload.data
    csrfParamName.value = context.csrf?.parameterName ?? ''
    csrfValue.value = context.csrf?.token ?? ''
    scopes.value = (context.scopes ?? []).map((item) => ({ code: item.scopeCode, description: item.scopeDesc }))
    selectedScopes.value = scopes.value.map((item) => item.code)

    if (!csrfParamName.value || !csrfValue.value) {
      throw new Error('授权上下文缺少 CSRF 参数，请重新登录')
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载授权上下文失败'
  } finally {
    loading.value = false
  }
}

function buildBackendConsentContextUrl() {
  const target = new URL('/oauth2/consent', backendOrigin)
  params.forEach((value, key) => {
    target.searchParams.append(key, value)
  })
  target.searchParams.set('format', 'json')
  return target.toString()
}

function submitConsent(action: 'approve' | 'deny') {
  if (submitting.value) {
    return
  }
  if (!csrfParamName.value || !csrfValue.value) {
    errorMessage.value = '授权参数已失效，请重新登录'
    return
  }
  if (action === 'approve' && selectedScopes.value.length === 0) {
    errorMessage.value = '请至少选择一个作用域后再继续'
    return
  }

  submitting.value = true
  const form = document.createElement('form')
  form.method = 'post'
  form.action = `${backendOrigin}/oauth2/authorize`

  appendHiddenInput(form, csrfParamName.value, csrfValue.value)
  appendHiddenInput(form, 'client_id', clientId)
  appendHiddenInput(form, 'state', state)
  appendHiddenInput(form, 'tenantId', tenantId)

  if (action === 'deny') {
    appendHiddenInput(form, 'consent_action', 'deny')
  } else {
    selectedScopes.value.forEach((scope) => appendHiddenInput(form, 'scope', scope))
  }

  document.body.appendChild(form)
  form.submit()
}

function appendHiddenInput(form: HTMLFormElement, name: string, value: string) {
  const input = document.createElement('input')
  input.type = 'hidden'
  input.name = name
  input.value = value
  form.appendChild(input)
}
</script>

<style scoped>
.consent-stage {
  min-height: calc(100vh - 120px);
  display: grid;
  place-items: center;
  padding: 24px;
}

.consent-panel {
  width: min(780px, 100%);
  display: grid;
  gap: 16px;
  padding: 24px;
  border-radius: 24px;
  border: 1px solid rgba(34, 48, 76, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 64px rgba(18, 35, 58, 0.12);
}

.consent-summary {
  color: #62708a;
  margin: 0;
  line-height: 1.7;
}

.consent-summary strong {
  color: #213552;
}

.scope-list {
  display: grid;
  gap: 12px;
}

.scope-item {
  display: grid;
  gap: 8px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid rgba(34, 48, 76, 0.12);
  background: rgba(236, 242, 255, 0.45);
}

.scope-name {
  font-weight: 700;
  color: #2a3d5c;
}

.scope-item p {
  margin: 0;
  color: #5f6c84;
  font-size: 13px;
}

.consent-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.consent-loading {
  padding: 10px 0;
}

@media (max-width: 720px) {
  .consent-actions {
    flex-direction: column;
  }

  .consent-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
