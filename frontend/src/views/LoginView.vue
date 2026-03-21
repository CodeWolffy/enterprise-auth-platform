<template>
  <div class="auth-stage">
    <section class="auth-panel auth-panel--hero">
      <span class="eyebrow">Frontend / Authorization Code + PKCE</span>
      <h1>企业级权限管理平台</h1>
      <p>
        当前前端已经直接联调 Spring Authorization Server。登录完成后会进入统一控制台，可继续管理
        OAuth2 客户端、用户、角色、权限、部门、租户、审计与系统配置。
      </p>
      <ul class="highlights">
        <li>公共客户端：`eap-frontend-spa`</li>
        <li>授权方式：Authorization Code + PKCE</li>
        <li>支持租户登录、中文登录页与中文同意页</li>
      </ul>
    </section>

    <section class="auth-panel auth-panel--form">
      <span class="eyebrow">Tenant Access</span>
      <h2>开始 OAuth2 登录</h2>
      <p>选择租户后，将跳转到后端统一认证中心继续完成授权。</p>

      <el-form label-position="top">
        <el-form-item label="租户编码">
          <el-select v-model="tenantId" placeholder="请选择租户" data-testid="login-tenant-select">
            <el-option label="平台租户 (platform)" value="platform" />
            <el-option label="租户 A (tenant-a)" value="tenant-a" />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="auth-actions">
        <el-button type="primary" size="large" :loading="loading" data-testid="login-submit" @click="handleLogin">
          跳转统一认证中心
        </el-button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const tenantId = ref('platform')
const loading = ref(false)

async function handleLogin() {
  loading.value = true
  try {
    await authStore.startLogin(tenantId.value)
  } finally {
    loading.value = false
  }
}
</script>
