<template>
  <section class="personal-center-page">
    <div class="personal-hero">
      <div class="hero-profile">
        <el-avatar :size="84" :src="profile?.avatarUrl || undefined" class="hero-avatar">
          {{ avatarName }}
        </el-avatar>
        <div class="hero-copy">
          <p class="eyebrow">Personal Center</p>
          <h1>个人中心</h1>
          <p class="summary">
            {{ isForcedPasswordChange ? '当前会话处于受限改密态，请先完成密码更新。' : '集中维护个人资料、头像、密码与在线设备。' }}
          </p>
          <div class="hero-tags">
            <el-tag :type="profile?.enabled === false ? 'danger' : 'success'" effect="dark">
              {{ profile?.enabled === false ? '账号停用' : '账号启用' }}
            </el-tag>
            <el-tag v-if="isForcedPasswordChange" type="warning" effect="dark">必须修改密码</el-tag>
            <el-tag v-else type="info" effect="plain">{{ profile?.tenantId || authStore.tenantId }}</el-tag>
          </div>
        </div>
      </div>
      <div class="hero-actions">
        <el-button :loading="loading" @click="loadProfile">刷新资料</el-button>
        <el-button type="primary" @click="openSessionDrawer">在线设备</el-button>
      </div>
    </div>

    <section class="overview-grid">
      <article class="overview-card">
        <span>资料完整度</span>
        <strong>{{ profileCompletion }}%</strong>
        <el-progress :percentage="profileCompletion" :show-text="false" />
        <small>{{ missingProfileItems.length ? `待补充：${missingProfileItems.join('、')}` : '资料已完整' }}</small>
      </article>
      <article class="overview-card">
        <span>安全状态</span>
        <strong>{{ securityScore }}%</strong>
        <el-progress :percentage="securityScore" :show-text="false" :status="securityScore >= 80 ? 'success' : undefined" />
        <small>{{ passwordAgeHint }}</small>
      </article>
      <article class="overview-card">
        <span>最近登录</span>
        <strong>{{ formatProfileDate(profile?.lastLoginAt, '暂无') }}</strong>
        <small>{{ profile?.lastLoginIp || '暂无登录 IP' }}</small>
      </article>
      <article class="overview-card">
        <span>在线设备</span>
        <strong>{{ activeSessionCount }}</strong>
        <small>当前仍在线的会话数量</small>
      </article>
    </section>

    <div class="content-grid">
      <el-card class="profile-card" shadow="never">
        <template #header>
          <div class="card-head">
            <span>个人资料</span>
            <el-tag size="small" type="info" effect="plain">可编辑</el-tag>
          </div>
        </template>

        <el-skeleton v-if="loading && !profile" :rows="8" animated />
        <template v-else>
          <div class="avatar-panel">
            <el-avatar :size="96" :src="profile?.avatarUrl || undefined" class="profile-avatar">
              {{ avatarName }}
            </el-avatar>
            <div class="avatar-copy">
              <strong>{{ profile?.displayName || profile?.username || '-' }}</strong>
              <span>{{ profile?.username || '-' }} · {{ profile?.tenantId || '-' }}</span>
              <el-upload
                :auto-upload="false"
                :show-file-list="false"
                accept="image/png,image/jpeg"
                :disabled="avatarUploadDisabled"
                :on-change="handleAvatarSelected"
              >
                <el-button type="primary" plain :disabled="avatarUploadDisabled">更换头像</el-button>
              </el-upload>
            </div>
          </div>

          <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-position="top" class="profile-form">
            <el-form-item label="显示名称" prop="displayName">
              <el-input v-model.trim="profileForm.displayName" maxlength="32" show-word-limit placeholder="用于页面展示" :disabled="profileFormDisabled" />
            </el-form-item>
            <el-form-item label="手机号" prop="mobile">
              <el-input v-model.trim="profileForm.mobile" maxlength="11" placeholder="11 位手机号" :disabled="profileFormDisabled" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model.trim="profileForm.email" maxlength="128" placeholder="用于密码找回和通知" :disabled="profileFormDisabled" />
            </el-form-item>
            <div class="form-actions">
              <el-button :disabled="profileFormDisabled" @click="resetProfileForm">重置</el-button>
              <el-button type="primary" :loading="profileSubmitting" :disabled="profileFormDisabled" @click="submitProfile">
                保存资料
              </el-button>
            </div>
          </el-form>

          <el-alert
            v-if="isForcedPasswordChange"
            class="inline-alert"
            type="warning"
            :closable="false"
            show-icon
            title="受限改密态下暂不允许修改个人资料，请先完成密码更新。"
          />
        </template>
      </el-card>

      <div class="right-stack">
        <el-card class="password-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>{{ isForcedPasswordChange ? '完成强制改密' : '修改密码' }}</span>
              <el-tag :type="isForcedPasswordChange ? 'warning' : 'success'" size="small" effect="plain">
                {{ isForcedPasswordChange ? '待处理' : '正常' }}
              </el-tag>
            </div>
          </template>

          <el-alert
            v-if="isForcedPasswordChange"
            class="inline-alert"
            type="warning"
            :closable="false"
            show-icon
            title="为了保护账号安全，请先更新密码。"
          />

          <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top" @submit.prevent>
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <div class="password-strength">
              <div class="strength-head">
                <span>密码强度</span>
                <strong>{{ passwordStrength.label }}</strong>
              </div>
              <el-progress :percentage="passwordStrength.score" :show-text="false" :status="passwordStrength.status" />
              <ul>
                <li v-for="item in passwordChecks" :key="item.label" :class="{ passed: item.passed }">{{ item.label }}</li>
              </ul>
            </div>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-button type="primary" :loading="passwordSubmitting" @click="submitPasswordChange">更新密码</el-button>
          </el-form>
        </el-card>

        <el-card class="security-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>账号与安全</span>
              <el-button link type="primary" @click="openSessionDrawer">管理设备</el-button>
            </div>
          </template>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户 ID">{{ profile?.id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前租户">{{ profile?.tenantId || authStore.tenantId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <div class="role-list">
                <el-tag v-for="role in roleTags" :key="role" size="small" effect="plain">{{ role }}</el-tag>
                <span v-if="!roleTags.length">-</span>
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="数据权限">{{ dataScopeLabel }}</el-descriptions-item>
            <el-descriptions-item label="密码更新时间">{{ formatProfileDate(profile?.passwordUpdatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="资料更新时间">{{ formatProfileDate(profile?.updatedAt) }}</el-descriptions-item>
            <el-descriptions-item label="账号创建时间">{{ formatProfileDate(profile?.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </div>
    </div>

    <el-card class="timeline-card" shadow="never">
      <template #header>
        <div class="card-head">
          <span>账号动态</span>
          <el-tag size="small" effect="plain">当前账号</el-tag>
        </div>
      </template>
      <div class="timeline-grid">
        <article v-for="item in timelineItems" :key="item.title" class="timeline-item">
          <span>{{ item.title }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </article>
      </div>
    </el-card>

    <el-dialog v-model="cropVisible" title="裁剪头像" width="520px" destroy-on-close>
      <div class="crop-dialog">
        <canvas ref="canvasRef" class="avatar-canvas" width="320" height="320" />
        <div class="crop-controls">
          <span>缩放</span>
          <el-slider v-model="cropScale" :min="1" :max="2.5" :step="0.05" @input="drawAvatarPreview" />
        </div>
        <p class="crop-hint">头像会按正方形裁剪并上传，仅支持 PNG/JPEG，建议小于 2MB。</p>
      </div>
      <template #footer>
        <el-button @click="cropVisible = false">取消</el-button>
        <el-button type="primary" :loading="avatarSubmitting" @click="submitAvatar">上传头像</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="sessionDrawerVisible" title="在线设备" size="860px">
      <el-alert
        title="可查看当前账号的在线会话，并将不再使用的设备强制下线。当前会话不能在这里下线。"
        type="info"
        show-icon
        :closable="false"
        class="inline-alert"
      />
      <div class="session-toolbar">
        <span>共 {{ sessionsList.length }} 个会话，{{ activeSessionCount }} 个在线</span>
        <div>
          <el-button size="small" :loading="sessionsLoading" @click="loadSessions">刷新</el-button>
          <el-button size="small" type="danger" plain :disabled="!otherActiveSessions.length" :loading="sessionActioning" @click="offlineOtherSessions">
            下线其他设备
          </el-button>
        </div>
      </div>
      <el-table v-loading="sessionsLoading" :data="sessionsList" stripe>
        <el-table-column label="标记" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.currentSession" type="success" effect="dark" size="small">当前</el-tag>
            <el-tag v-else-if="row.active" type="info" size="small">在线</el-tag>
            <el-tag v-else type="info" effect="plain" size="small">离线</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="设备" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ formatDevice(row.device) }}</template>
        </el-table-column>
        <el-table-column prop="clientIp" label="登录 IP" width="140" />
        <el-table-column label="首次登录" width="180">
          <template #default="{ row }">{{ formatSessionTime(row.issuedAt) }}</template>
        </el-table-column>
        <el-table-column label="最后访问" width="180">
          <template #default="{ row }">{{ formatSessionTime(row.lastAccessAt) }}</template>
        </el-table-column>
        <el-table-column label="过期时间" width="180">
          <template #default="{ row }">{{ formatSessionTime(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" :disabled="!row.active || row.currentSession" @click="kickSession(row.sessionId)">下线</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无在线设备" />
        </template>
      </el-table>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  changeAccountPassword,
  fetchAccountProfile,
  updateAccountProfile,
  uploadAccountAvatar,
  type AccountProfileResponse,
} from '@/api/modules/account'
import { forceOffline, querySessions } from '@/api/modules/auth'
import { useAuthStore } from '@/stores/auth'
import type { UserSessionView } from '@/types/auth-models'
import { formatDateTime } from '@/utils/datetime'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(false)
const profileSubmitting = ref(false)
const passwordSubmitting = ref(false)
const avatarSubmitting = ref(false)
const sessionsLoading = ref(false)
const sessionActioning = ref(false)
const profile = ref<AccountProfileResponse | null>(null)
const sessionsList = ref<UserSessionView[]>([])
const profileFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const cropVisible = ref(false)
const sessionDrawerVisible = ref(false)
const cropScale = ref(1)
const canvasRef = ref<HTMLCanvasElement | null>(null)
const selectedAvatarName = ref('avatar.png')
let cropImage: HTMLImageElement | null = null

const profileForm = reactive({
  displayName: '',
  mobile: '',
  email: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const profileRules: FormRules = {
  displayName: [{ max: 32, message: '显示名称不能超过32个字符', trigger: ['blur', 'change'] }],
  mobile: [{ pattern: /^1\d{10}$/, message: '请输入有效的 11 位手机号', trigger: ['blur', 'change'] }],
  email: [
    { type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] },
    { max: 128, message: '邮箱不能超过128个字符', trigger: ['blur', 'change'] },
  ],
}

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度必须在8到64位之间', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!validatePasswordComposition(String(value || ''))) {
          callback(new Error('新密码需包含字母和数字，且不能包含空白字符'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const isForcedPasswordChange = computed(() => authStore.passwordChangeRequired || profile.value?.mustChangePassword === true)
const profileFormDisabled = computed(() => loading.value || profileSubmitting.value || isForcedPasswordChange.value || !profile.value)
const avatarUploadDisabled = computed(() => loading.value || avatarSubmitting.value || isForcedPasswordChange.value || !profile.value)
const avatarName = computed(() => {
  const source = profile.value?.displayName || profile.value?.username || authStore.snapshot?.username || 'U'
  return source.trim().charAt(0).toUpperCase()
})
const roleTags = computed(() => authStore.snapshot?.roles ?? [])
const activeSessionCount = computed(() => sessionsList.value.filter((session) => session.active).length)
const otherActiveSessions = computed(() => sessionsList.value.filter((session) => session.active && !session.currentSession))
const dataScopeLabel = computed(() => {
  const type = authStore.snapshot?.dataScopeType
  const labels: Record<string, string> = {
    ALL: '全部数据',
    TENANT: '当前租户',
    DEPT: '本部门',
    DEPT_AND_CHILD: '本部门及下级',
    SELF: '仅本人',
    CUSTOM: '自定义部门',
  }
  return type ? labels[type] ?? type : '-'
})
const missingProfileItems = computed(() => {
  const missing: string[] = []
  if (!profile.value?.displayName) missing.push('显示名称')
  if (!profile.value?.mobile) missing.push('手机号')
  if (!profile.value?.email) missing.push('邮箱')
  if (!profile.value?.avatarUrl) missing.push('头像')
  return missing
})
const profileCompletion = computed(() => Math.round(((4 - missingProfileItems.value.length) / 4) * 100))
const passwordFresh = computed(() => {
  const updatedAt = profile.value?.passwordUpdatedAt
  if (!updatedAt) return false
  const timestamp = Date.parse(updatedAt)
  if (!Number.isFinite(timestamp)) return false
  return Date.now() - timestamp <= 1000 * 60 * 60 * 24 * 90
})
const securityScore = computed(() => {
  let score = 45
  if (!isForcedPasswordChange.value) score += 25
  if (passwordFresh.value) score += 15
  if (profile.value?.email) score += 10
  if (activeSessionCount.value <= 2) score += 5
  return Math.min(score, 100)
})
const passwordAgeHint = computed(() => {
  if (isForcedPasswordChange.value) return '需要立即修改密码'
  if (!profile.value?.passwordUpdatedAt) return '暂无密码更新时间'
  return passwordFresh.value ? '密码近期已更新' : '建议定期更新密码'
})
const passwordChecks = computed(() => {
  const value = passwordForm.newPassword
  return [
    { label: '8-64 位长度', passed: value.length >= 8 && value.length <= 64 },
    { label: '包含字母', passed: /[A-Za-z]/.test(value) },
    { label: '包含数字', passed: /\d/.test(value) },
    { label: '包含特殊字符', passed: /[^A-Za-z0-9]/.test(value) },
    { label: '不含空白字符', passed: value.length > 0 && !/\s/.test(value) },
  ]
})
const passwordStrength = computed(() => {
  const passed = passwordChecks.value.filter((item) => item.passed).length
  const score = Math.round((passed / passwordChecks.value.length) * 100)
  if (score >= 100) return { score, label: '强', status: 'success' as const }
  if (score >= 60) return { score, label: '中', status: undefined }
  return { score, label: '弱', status: 'exception' as const }
})
const timelineItems = computed(() => [
  { title: '账号创建', value: formatProfileDate(profile.value?.createdAt), hint: profile.value?.tenantId || '-' },
  { title: '资料更新', value: formatProfileDate(profile.value?.updatedAt), hint: profile.value?.displayName || profile.value?.username || '-' },
  { title: '密码更新', value: formatProfileDate(profile.value?.passwordUpdatedAt), hint: passwordAgeHint.value },
  { title: '最近登录', value: formatProfileDate(profile.value?.lastLoginAt), hint: profile.value?.lastLoginIp || '-' },
])

async function loadProfile() {
  loading.value = true
  try {
    const nextProfile = await fetchAccountProfile()
    profile.value = nextProfile
    syncProfileForm(nextProfile)
  } finally {
    loading.value = false
  }
}

function syncProfileForm(nextProfile: AccountProfileResponse | null) {
  profileForm.displayName = nextProfile?.displayName ?? ''
  profileForm.mobile = nextProfile?.mobile ?? ''
  profileForm.email = nextProfile?.email ?? ''
}

function resetProfileForm() {
  syncProfileForm(profile.value)
  profileFormRef.value?.clearValidate()
}

async function submitProfile() {
  await profileFormRef.value?.validate()
  profileSubmitting.value = true
  try {
    const updated = await updateAccountProfile({
      displayName: normalizeOptional(profileForm.displayName),
      mobile: normalizeOptional(profileForm.mobile),
      email: normalizeOptional(profileForm.email),
    })
    profile.value = updated
    syncProfileForm(updated)
    await authStore.bootstrapSnapshot()
    ElMessage.success('个人资料已更新')
  } finally {
    profileSubmitting.value = false
  }
}

function normalizeOptional(value: string) {
  const trimmed = value.trim()
  return trimmed || null
}

function handleAvatarSelected(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  if (!['image/png', 'image/jpeg'].includes(raw.type)) {
    ElMessage.warning('头像仅支持 PNG/JPEG')
    return
  }
  if (raw.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像文件不能超过 2MB')
    return
  }
  selectedAvatarName.value = raw.name
  const imageUrl = URL.createObjectURL(raw)
  const image = new Image()
  image.onload = () => {
    URL.revokeObjectURL(imageUrl)
    cropImage = image
    cropScale.value = 1
    cropVisible.value = true
    void nextTick(drawAvatarPreview)
  }
  image.onerror = () => {
    URL.revokeObjectURL(imageUrl)
    ElMessage.error('头像图片读取失败')
  }
  image.src = imageUrl
}

function drawAvatarPreview() {
  const canvas = canvasRef.value
  if (!canvas || !cropImage) return
  const context = canvas.getContext('2d')
  if (!context) return
  const size = canvas.width
  context.clearRect(0, 0, size, size)
  context.fillStyle = '#f8fafc'
  context.fillRect(0, 0, size, size)

  const image = cropImage
  const baseScale = Math.max(size / image.width, size / image.height)
  const scale = baseScale * cropScale.value
  const width = image.width * scale
  const height = image.height * scale
  const left = (size - width) / 2
  const top = (size - height) / 2
  context.drawImage(image, left, top, width, height)

  context.strokeStyle = 'rgba(255, 255, 255, 0.9)'
  context.lineWidth = 3
  context.strokeRect(1.5, 1.5, size - 3, size - 3)
}

async function submitAvatar() {
  const canvas = canvasRef.value
  if (!canvas) return
  avatarSubmitting.value = true
  try {
    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob((result) => {
        if (result) {
          resolve(result)
          return
        }
        reject(new Error('头像裁剪失败'))
      }, 'image/png', 0.92)
    })
    const filename = selectedAvatarName.value.replace(/\.[^.]+$/, '') || 'avatar'
    const file = new File([blob], `${filename}.png`, { type: 'image/png' })
    const updated = await uploadAccountAvatar(file)
    profile.value = updated
    syncProfileForm(updated)
    await authStore.bootstrapSnapshot()
    cropVisible.value = false
    ElMessage.success('头像已更新')
  } finally {
    avatarSubmitting.value = false
  }
}

async function submitPasswordChange() {
  await passwordFormRef.value?.validate()
  passwordSubmitting.value = true
  const wasForced = isForcedPasswordChange.value
  try {
    const updated = await changeAccountPassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
    profile.value = updated
    syncProfileForm(updated)
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
    passwordFormRef.value?.clearValidate()
    authStore.clearPasswordChangeRequirement()
    await authStore.bootstrapSnapshot()
    ElMessage.success(wasForced ? '密码已更新，请继续使用控制台' : '密码已更新')
    if (wasForced) {
      await router.replace('/dashboard')
    }
  } finally {
    passwordSubmitting.value = false
  }
}

function validatePasswordComposition(value: string) {
  return /[A-Za-z]/.test(value) && /\d/.test(value) && !/\s/.test(value)
}

async function openSessionDrawer() {
  sessionDrawerVisible.value = true
  await loadSessions()
}

async function loadSessions() {
  sessionsLoading.value = true
  try {
    const sessions = await querySessions('own')
    sessionsList.value = Array.isArray(sessions) ? sessions : sessions.records
  } finally {
    sessionsLoading.value = false
  }
}

async function kickSession(sessionId: string) {
  try {
    await ElMessageBox.confirm('下线后该设备将立即失去访问权限，是否继续？', '下线确认', { type: 'warning' })
    sessionActioning.value = true
    await forceOffline(sessionId)
    ElMessage.success('设备已下线')
    await loadSessions()
  } catch {
    // 用户取消或请求失败时保持现状。
  } finally {
    sessionActioning.value = false
  }
}

async function offlineOtherSessions() {
  try {
    await ElMessageBox.confirm(`确认下线 ${otherActiveSessions.value.length} 个其他在线设备？`, '批量下线确认', { type: 'warning' })
    sessionActioning.value = true
    await Promise.all(otherActiveSessions.value.map((session) => forceOffline(session.sessionId)))
    ElMessage.success('其他在线设备已下线')
    await loadSessions()
  } catch {
    // 用户取消或请求失败时保持现状。
  } finally {
    sessionActioning.value = false
  }
}

function formatProfileDate(value?: string | null, placeholder = '-') {
  if (!value) return placeholder
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(timestamp))
}

function formatSessionTime(epochMs?: number | null) {
  return formatDateTime(epochMs)
}

function formatDevice(raw?: string | null) {
  if (!raw) return 'Unknown'
  const ua = raw.toLowerCase()
  if (ua.includes('edg/')) return 'Microsoft Edge'
  if (ua.includes('chrome/')) return 'Google Chrome'
  if (ua.includes('firefox/')) return 'Mozilla Firefox'
  if (ua.includes('safari/') && !ua.includes('chrome/')) return 'Safari'
  if (ua.includes('java-http-client')) return 'Browser Session'
  return raw
}

watch(cropScale, drawAvatarPreview)
onMounted(() => {
  void loadProfile()
  void loadSessions()
})
</script>

<style scoped lang="scss">
.personal-center-page {
  min-height: calc(100vh - 140px);
  padding: 28px;
  background:
    radial-gradient(circle at 12% 8%, rgba(20, 184, 166, 0.22), transparent 30%),
    radial-gradient(circle at 92% 0%, rgba(15, 23, 42, 0.12), transparent 28%),
    linear-gradient(135deg, #f6f8fb 0%, #edf2f7 100%);
}

.personal-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
  padding: 28px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(16px);
}

.hero-profile {
  display: flex;
  align-items: center;
  gap: 20px;
  min-width: 0;
}

.hero-avatar,
.profile-avatar {
  flex: 0 0 auto;
  border: 4px solid #fff;
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.14);
}

.hero-copy {
  min-width: 0;
}

.eyebrow {
  margin: 0 0 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  color: #0f172a;
  font-size: 32px;
  line-height: 1.15;
}

.summary {
  max-width: 660px;
  margin: 10px 0 0;
  color: #52657a;
  line-height: 1.7;
}

.hero-tags,
.role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-tags {
  margin-top: 14px;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 22px;
}

.overview-card {
  display: grid;
  gap: 10px;
  min-height: 136px;
  padding: 20px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 14px 42px rgba(15, 23, 42, 0.06);

  span {
    color: #64748b;
    font-size: 13px;
  }

  strong {
    color: #0f172a;
    font-size: 24px;
    line-height: 1.2;
  }

  small {
    color: #708090;
    line-height: 1.5;
  }
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(360px, 0.92fr) minmax(420px, 1.08fr);
  gap: 22px;
}

.right-stack {
  display: grid;
  gap: 22px;
}

.profile-card,
.password-card,
.security-card,
.timeline-card {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 22px;
  overflow: hidden;
}

:deep(.el-card__header) {
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.82), rgba(255, 255, 255, 0.96));
}

.card-head,
.session-toolbar,
.strength-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-head {
  font-weight: 700;
  color: #0f172a;
}

.avatar-panel {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 20px;
  padding: 18px;
  border: 1px solid rgba(20, 184, 166, 0.18);
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.1), rgba(14, 165, 233, 0.08));
}

.avatar-copy {
  display: grid;
  gap: 8px;

  strong {
    color: #0f172a;
    font-size: 20px;
  }

  span {
    color: #64748b;
  }
}

.profile-form {
  margin-top: 4px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.inline-alert {
  margin-bottom: 18px;
}

.password-strength {
  margin: -4px 0 18px;
  padding: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  background: #f8fafc;

  ul {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px 14px;
    margin: 12px 0 0;
    padding: 0;
    list-style: none;
  }

  li {
    color: #94a3b8;
    font-size: 13px;

    &::before {
      content: '·';
      margin-right: 6px;
      color: #cbd5e1;
    }

    &.passed {
      color: #0f766e;

      &::before {
        color: #14b8a6;
      }
    }
  }
}

.timeline-card {
  margin-top: 22px;
}

.timeline-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.timeline-item {
  display: grid;
  gap: 8px;
  padding: 16px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 16px;
  background: #f8fafc;

  span {
    color: #64748b;
    font-size: 13px;
  }

  strong {
    color: #0f172a;
    font-size: 16px;
  }

  small {
    color: #708090;
  }
}

.crop-dialog {
  display: grid;
  justify-items: center;
  gap: 18px;
}

.avatar-canvas {
  width: 320px;
  height: 320px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08), 0 18px 44px rgba(15, 23, 42, 0.14);
}

.crop-controls {
  width: 100%;
  display: grid;
  grid-template-columns: 48px 1fr;
  align-items: center;
  gap: 12px;
}

.crop-hint {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.session-toolbar {
  margin-bottom: 14px;
  color: #52657a;
}

@media (max-width: 1180px) {
  .overview-grid,
  .timeline-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .personal-center-page {
    padding: 18px;
  }

  .personal-hero,
  .hero-profile,
  .hero-actions,
  .avatar-panel,
  .session-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .overview-grid,
  .timeline-grid {
    grid-template-columns: 1fr;
  }

  .password-strength ul {
    grid-template-columns: 1fr;
  }
}
</style>