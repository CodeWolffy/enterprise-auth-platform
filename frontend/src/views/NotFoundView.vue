<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-logo">
        <span class="auth-logo__mark">404</span>
      </div>
      <h1 class="auth-title">页面未找到</h1>
      <p class="auth-subtitle">您访问的页面不存在或已被移除</p>

      <div class="notfound-illustration">
        <svg viewBox="0 0 200 120" fill="none" xmlns="http://www.w3.org/2000/svg">
          <!-- 搜索框 -->
          <rect x="20" y="20" width="160" height="40" rx="8" fill="#f1f5f9" stroke="#e2e8f0" stroke-width="2"/>
          <circle cx="40" cy="40" r="8" fill="#94a3b8"/>
          <rect x="56" y="36" width="80" height="8" rx="4" fill="#cbd5e1"/>
          <!-- 放大镜 -->
          <circle cx="155" cy="40" r="12" stroke="#2563eb" stroke-width="3" fill="none"/>
          <line x1="164" y1="49" x2="172" y2="57" stroke="#2563eb" stroke-width="3" stroke-linecap="round"/>
          <!-- 问号 -->
          <text x="95" y="95" font-size="32" font-weight="600" fill="#2563eb" text-anchor="middle">?</text>
          <!-- 装饰点 -->
          <circle cx="30" cy="100" r="4" fill="#dbeafe"/>
          <circle cx="170" cy="100" r="4" fill="#dbeafe"/>
          <circle cx="50" cy="110" r="3" fill="#e0e7ff"/>
          <circle cx="150" cy="110" r="3" fill="#e0e7ff"/>
        </svg>
      </div>

      <div class="auth-form">
        <div class="auth-btn-group">
          <button class="auth-submit" type="button" @click="router.push('/dashboard')">
            返回首页
          </button>
          <button class="auth-submit auth-submit--secondary" type="button" @click="goBack">
            返回上一页
          </button>
        </div>
      </div>

      <p class="auth-switch">
        需要帮助？<button class="auth-link" type="button" @click="router.push('/login')">重新登录</button>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/dashboard')
}
</script>

<style scoped>
.auth-page {
  --ink: #0f172a;
  --muted: #64748b;
  --line: #e2e8f0;
  --accent: #2563eb;
  --accent-hover: #1d4ed8;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  padding: 2rem 1.5rem;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 16px;
  border: 1px solid var(--line);
  padding: 2.5rem 2rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  text-align: center;
}

.auth-logo {
  display: flex;
  justify-content: center;
  margin-bottom: 1.5rem;
}

.auth-logo__mark {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: #fff;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 1.5rem;
  letter-spacing: 0.02em;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}

.auth-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--ink);
  text-align: center;
  margin: 0 0 0.5rem;
  letter-spacing: -0.01em;
}

.auth-subtitle {
  font-size: 0.875rem;
  color: var(--muted);
  text-align: center;
  margin: 0 0 1.5rem;
}

.notfound-illustration {
  margin: 1.5rem 0;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 12px;
  border: 1px solid var(--line);
}

.notfound-illustration svg {
  width: 100%;
  max-width: 200px;
  height: auto;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.auth-btn-group {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.auth-submit {
  width: 100%;
  padding: 0.75rem;
  border: none;
  border-radius: 10px;
  background: var(--accent);
  color: #fff;
  font-size: 0.9375rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}

.auth-submit:hover {
  background: var(--accent-hover);
}

.auth-submit:active {
  background: #1e40af;
}

.auth-submit--secondary {
  background: #fff;
  color: var(--ink);
  border: 1px solid var(--line);
}

.auth-submit--secondary:hover {
  background: #f8fafc;
  border-color: #cbd5e1;
}

.auth-submit--secondary:active {
  background: #f1f5f9;
}

.auth-switch {
  margin: 1.5rem 0 0;
  text-align: center;
  font-size: 0.8125rem;
  color: var(--muted);
}

.auth-link {
  color: var(--accent);
  background: none;
  border: none;
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  font-size: inherit;
  transition: color 0.15s;
}

.auth-link:hover {
  color: var(--accent-hover);
}

@media (max-width: 480px) {
  .auth-card {
    padding: 2rem 1.25rem;
    border-radius: 12px;
  }

  .auth-logo__mark {
    width: 56px;
    height: 56px;
    font-size: 1.25rem;
  }
}
</style>
