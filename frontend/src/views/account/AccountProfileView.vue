<template>
  <section class="account-profile-page">
    <div class="account-hero">
      <div>
        <p class="eyebrow">Account Security</p>
        <h1>我的账号</h1>
        <p class="summary">
          {{ authStore.passwordChangeRequired ? '当前会话处于受限改密态，完成密码更新后即可进入控制台。' : '管理当前登录账号的基础信息与密码安全。' }}
        </p>
      </div>
      <el-tag v-if="authStore.passwordChangeRequired" type="warning" effect="dark">必须修改密码</el-tag>
      <el-tag v-else type="success" effect="dark">会话正常</el-tag>
    </div>

    <div class="account-grid">
      <el-card class="profile-card" shadow="never">
        <template #header>
          <span>账号资料</span>
        </template>
        <el-skeleton v-if="loading" :rows="5" animated />
        <el-descriptions v-else :column="1" border>
          <el-descriptions-item label="用户名">{{ profile?.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="显示名称">{{ profile?.displayName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ profile?.mobile || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ profile?.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="密码更新时间">{{ profile?.passwordUpdatedAt || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="password-card" shadow="never">
        <template #header>
          <span>{{ authStore.passwordChangeRequired ? '完成强制改密' : '修改密码' }}</span>
        </template>
        <el-alert
          v-if="authStore.passwordChangeRequired"
          class="restriction-alert"
          type="warning"
          :closable="false"
          show-icon
          title="为了保护账号安全，请先更新密码。"
        />
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
          </el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitPasswordChange">更新密码</el-button>
        </el-form>
      </el-card>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { changeAccountPassword, fetchAccountProfile, type AccountProfileResponse } from '@/api/modules/account'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const submitting = ref(false)
const profile = ref<AccountProfileResponse | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度必须在8到64位之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function loadProfile() {
  if (authStore.passwordChangeRequired) {
    return
  }
  loading.value = true
  try {
    profile.value = await fetchAccountProfile()
  } finally {
    loading.value = false
  }
}

async function submitPasswordChange() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    profile.value = await changeAccountPassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    authStore.clearPasswordChangeRequirement()
    await authStore.bootstrapSnapshot()
    ElMessage.success('密码已更新，请继续使用控制台')
    await router.replace('/dashboard')
  } finally {
    submitting.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped lang="scss">
.account-profile-page {
  min-height: calc(100vh - 140px);
  padding: 28px;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.18), transparent 34%),
    linear-gradient(135deg, #f7f9fc 0%, #eef3f9 100%);
}

.account-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
  padding: 28px;
  border: 1px solid rgba(23, 35, 61, 0.08);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 20px 60px rgba(31, 45, 61, 0.08);
  backdrop-filter: blur(14px);
}

.eyebrow {
  margin: 0 0 8px;
  color: #6b7a90;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  color: #17233d;
  font-size: 30px;
  line-height: 1.2;
}

.summary {
  max-width: 620px;
  margin: 10px 0 0;
  color: #5c6b82;
  line-height: 1.7;
}

.account-grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(340px, 1.1fr);
  gap: 22px;
}

.profile-card,
.password-card {
  border: 1px solid rgba(23, 35, 61, 0.08);
  border-radius: 20px;
}

.restriction-alert {
  margin-bottom: 18px;
}

@media (max-width: 960px) {
  .account-grid {
    grid-template-columns: 1fr;
  }
}
</style>