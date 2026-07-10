<script setup lang="ts">
import type { FormInstance, FormRules, UploadFile } from 'element-plus';

import type { AccountProfileResponse } from '#/api/account';
import type { UserSessionView } from '#/types/auth-models';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import {
  ElAlert,
  ElAvatar,
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElProgress,
  ElSkeleton,
  ElSlider,
  ElTable,
  ElTableColumn,
  ElTag,
  ElUpload,
} from 'element-plus';

import {
  changeAccountPassword,
  fetchAccountProfile,
  updateAccountProfile,
  uploadAccountAvatar,
} from '#/api/account';
import { forceOffline, querySessions } from '#/api/auth-session';
import { useAuthStore } from '#/store/auth';
import { formatDateTime } from '#/utils/datetime';

const authStore = useAuthStore();
const router = useRouter();

const loading = ref(false);
const profileSubmitting = ref(false);
const passwordSubmitting = ref(false);
const avatarSubmitting = ref(false);
const sessionsLoading = ref(false);
const sessionActioning = ref(false);
const profile = ref<AccountProfileResponse | null>(null);
const sessionsList = ref<UserSessionView[]>([]);
const profileFormRef = ref<FormInstance>();
const passwordFormRef = ref<FormInstance>();
const cropVisible = ref(false);
const sessionDrawerVisible = ref(false);
const cropScale = ref(1);
const canvasRef = ref<HTMLCanvasElement | null>(null);
const selectedAvatarName = ref('avatar.png');
let cropImage: HTMLImageElement | null = null;

const profileForm = reactive({
  displayName: '',
  mobile: '',
  email: '',
});

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

const profileRules: FormRules = {
  displayName: [
    {
      max: 32,
      message: '显示名称不能超过32个字符',
      trigger: ['blur', 'change'],
    },
  ],
  mobile: [
    {
      pattern: /^1\d{10}$/,
      message: '请输入有效的 11 位手机号',
      trigger: ['blur', 'change'],
    },
  ],
  email: [
    {
      type: 'email',
      message: '请输入有效的邮箱地址',
      trigger: ['blur', 'change'],
    },
    { max: 128, message: '邮箱不能超过128个字符', trigger: ['blur', 'change'] },
  ],
};

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度必须在8到64位之间', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!validatePasswordComposition(String(value || ''))) {
          callback(new Error('新密码需包含字母和数字，且不能包含空白字符'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'));
          return;
        }
        callback();
      },
      trigger: 'blur',
    },
  ],
};

const isForcedPasswordChange = computed(
  () =>
    authStore.passwordChangeRequired ||
    profile.value?.mustChangePassword === true,
);
const profileFormDisabled = computed(
  () =>
    loading.value ||
    profileSubmitting.value ||
    isForcedPasswordChange.value ||
    !profile.value,
);
const avatarUploadDisabled = computed(
  () =>
    loading.value ||
    avatarSubmitting.value ||
    isForcedPasswordChange.value ||
    !profile.value,
);
const avatarName = computed(() => {
  const source =
    profile.value?.displayName ||
    profile.value?.username ||
    authStore.snapshot?.username ||
    'U';
  return source.trim().charAt(0).toUpperCase();
});
const passwordFormUsername = computed(
  () => profile.value?.username || authStore.snapshot?.username || '',
);
const roleTags = computed(() => authStore.snapshot?.roles ?? []);
const activeSessionCount = computed(
  () => sessionsList.value.filter((session) => session.active).length,
);
const otherActiveSessions = computed(() =>
  sessionsList.value.filter(
    (session) => session.active && !session.currentSession,
  ),
);
const dataScopeLabel = computed(() => {
  const type = authStore.snapshot?.dataScopeType;
  const labels: Record<string, string> = {
    ALL: '全部数据',
    TENANT: '当前租户',
    DEPT: '本部门',
    DEPT_AND_CHILD: '本部门及下级',
    SELF: '仅本人',
    CUSTOM: '自定义部门',
  };
  return type ? (labels[type] ?? type) : '-';
});
const missingProfileItems = computed(() => {
  const missing: string[] = [];
  if (!profile.value?.displayName) missing.push('显示名称');
  if (!profile.value?.mobile) missing.push('手机号');
  if (!profile.value?.email) missing.push('邮箱');
  if (!profile.value?.avatarUrl) missing.push('头像');
  return missing;
});
const profileCompletion = computed(() =>
  Math.round(((4 - missingProfileItems.value.length) / 4) * 100),
);
const passwordFresh = computed(() => {
  const updatedAt = profile.value?.passwordUpdatedAt;
  if (!updatedAt) return false;
  const timestamp = Date.parse(updatedAt);
  if (!Number.isFinite(timestamp)) return false;
  return Date.now() - timestamp <= 1000 * 60 * 60 * 24 * 90;
});
const securityScore = computed(() => {
  let score = 45;
  if (!isForcedPasswordChange.value) score += 25;
  if (passwordFresh.value) score += 15;
  if (profile.value?.email) score += 10;
  if (activeSessionCount.value <= 2) score += 5;
  return Math.min(score, 100);
});
const passwordAgeHint = computed(() => {
  if (isForcedPasswordChange.value) return '需要立即修改密码';
  if (!profile.value?.passwordUpdatedAt) return '暂无密码更新时间';
  return passwordFresh.value ? '密码近期已更新' : '建议定期更新密码';
});
const passwordChecks = computed(() => {
  const value = passwordForm.newPassword;
  return [
    { label: '8-64 位长度', passed: value.length >= 8 && value.length <= 64 },
    { label: '包含字母', passed: /[A-Za-z]/.test(value) },
    { label: '包含数字', passed: /\d/.test(value) },
    { label: '包含特殊字符', passed: /[^A-Za-z0-9]/.test(value) },
    { label: '不含空白字符', passed: value.length > 0 && !/\s/.test(value) },
  ];
});
const passwordStrength = computed(() => {
  const passed = passwordChecks.value.filter((item) => item.passed).length;
  const score = Math.round((passed / passwordChecks.value.length) * 100);
  if (score >= 100) return { score, label: '强', status: 'success' as const };
  if (score >= 60) return { score, label: '中', status: undefined };
  return { score, label: '弱', status: 'exception' as const };
});
const timelineItems = computed(() => [
  {
    title: '账号创建',
    value: formatProfileDate(profile.value?.createdAt),
    hint: profile.value?.tenantId || '-',
  },
  {
    title: '资料更新',
    value: formatProfileDate(profile.value?.updatedAt),
    hint: profile.value?.displayName || profile.value?.username || '-',
  },
  {
    title: '密码更新',
    value: formatProfileDate(profile.value?.passwordUpdatedAt),
    hint: passwordAgeHint.value,
  },
  {
    title: '最近登录',
    value: formatProfileDate(profile.value?.lastLoginAt),
    hint: profile.value?.lastLoginIp || '-',
  },
]);

async function loadProfile() {
  if (isForcedPasswordChange.value) {
    return;
  }
  loading.value = true;
  try {
    const nextProfile = await fetchAccountProfile();
    profile.value = nextProfile;
    syncProfileForm(nextProfile);
  } finally {
    loading.value = false;
  }
}

function syncProfileForm(nextProfile: AccountProfileResponse | null) {
  profileForm.displayName = nextProfile?.displayName ?? '';
  profileForm.mobile = nextProfile?.mobile ?? '';
  profileForm.email = nextProfile?.email ?? '';
}

function resetProfileForm() {
  syncProfileForm(profile.value);
  profileFormRef.value?.clearValidate();
}

async function submitProfile() {
  await profileFormRef.value?.validate();
  profileSubmitting.value = true;
  try {
    const updated = await updateAccountProfile({
      displayName: normalizeOptional(profileForm.displayName),
      mobile: normalizeOptional(profileForm.mobile),
      email: normalizeOptional(profileForm.email),
    });
    profile.value = updated;
    syncProfileForm(updated);
    await authStore.bootstrapSnapshot();
    ElMessage.success('个人资料已更新');
  } finally {
    profileSubmitting.value = false;
  }
}

function normalizeOptional(value: string) {
  const trimmed = value.trim();
  return trimmed || null;
}

function handleAvatarSelected(uploadFile: UploadFile) {
  const raw = uploadFile.raw;
  if (!raw) return;
  if (!['image/jpeg', 'image/png'].includes(raw.type)) {
    ElMessage.warning('头像仅支持 PNG/JPEG');
    return;
  }
  if (raw.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像文件不能超过 2MB');
    return;
  }
  selectedAvatarName.value = raw.name;
  const imageUrl = URL.createObjectURL(raw);
  const image = new Image();
  image.addEventListener('load', () => {
    URL.revokeObjectURL(imageUrl);
    cropImage = image;
    cropScale.value = 1;
    cropVisible.value = true;
    void nextTick(drawAvatarPreview);
  });
  image.addEventListener('error', () => {
    URL.revokeObjectURL(imageUrl);
    ElMessage.error('头像图片读取失败');
  });
  image.src = imageUrl;
}

function drawAvatarPreview() {
  const canvas = canvasRef.value;
  if (!canvas || !cropImage) return;
  const context = canvas.getContext('2d');
  if (!context) return;
  const size = canvas.width;
  context.clearRect(0, 0, size, size);
  context.fillStyle = '#f8fafc';
  context.fillRect(0, 0, size, size);

  const image = cropImage;
  const baseScale = Math.max(size / image.width, size / image.height);
  const scale = baseScale * cropScale.value;
  const width = image.width * scale;
  const height = image.height * scale;
  const left = (size - width) / 2;
  const top = (size - height) / 2;
  context.drawImage(image, left, top, width, height);

  context.strokeStyle = 'rgba(255, 255, 255, 0.9)';
  context.lineWidth = 3;
  context.strokeRect(1.5, 1.5, size - 3, size - 3);
}

async function submitAvatar() {
  const canvas = canvasRef.value;
  if (!canvas) return;
  avatarSubmitting.value = true;
  try {
    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob(
        (result) => {
          if (result) {
            resolve(result);
            return;
          }
          reject(new Error('头像裁剪失败'));
        },
        'image/png',
        0.92,
      );
    });
    const filename =
      selectedAvatarName.value.replace(/\.[^.]+$/, '') || 'avatar';
    const file = new File([blob], `${filename}.png`, { type: 'image/png' });
    const updated = await uploadAccountAvatar(file);
    profile.value = updated;
    syncProfileForm(updated);
    await authStore.bootstrapSnapshot();
    cropVisible.value = false;
    ElMessage.success('头像已更新');
  } finally {
    avatarSubmitting.value = false;
  }
}

async function submitPasswordChange() {
  await passwordFormRef.value?.validate();
  passwordSubmitting.value = true;
  const wasForced = isForcedPasswordChange.value;
  try {
    const updated = await changeAccountPassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    });
    profile.value = updated;
    syncProfileForm(updated);
    passwordForm.oldPassword = '';
    passwordForm.newPassword = '';
    passwordForm.confirmPassword = '';
    passwordFormRef.value?.clearValidate();
    authStore.clearPasswordChangeRequirement();
    await authStore.bootstrapSnapshot();
    ElMessage.success(
      wasForced ? '密码已更新，请继续使用控制台' : '密码已更新',
    );
    if (wasForced) {
      router.replace('/dashboard');
    }
  } finally {
    passwordSubmitting.value = false;
  }
}

function validatePasswordComposition(value: string) {
  return /[A-Za-z]/.test(value) && /\d/.test(value) && !/\s/.test(value);
}

async function openSessionDrawer() {
  if (isForcedPasswordChange.value) {
    return;
  }
  sessionDrawerVisible.value = true;
  await loadSessions();
}

async function loadSessions() {
  if (isForcedPasswordChange.value) {
    sessionsList.value = [];
    return;
  }
  sessionsLoading.value = true;
  try {
    const sessions = await querySessions('own');
    sessionsList.value = Array.isArray(sessions) ? sessions : sessions.records;
  } finally {
    sessionsLoading.value = false;
  }
}

async function kickSession(sessionId: string) {
  try {
    await ElMessageBox.confirm(
      '下线后该设备将立即失去访问权限，是否继续？',
      '下线确认',
      { type: 'warning' },
    );
    sessionActioning.value = true;
    await forceOffline(sessionId);
    ElMessage.success('设备已下线');
    await loadSessions();
  } catch {
    // noop
  } finally {
    sessionActioning.value = false;
  }
}

async function offlineOtherSessions() {
  try {
    await ElMessageBox.confirm(
      `确认下线 ${otherActiveSessions.value.length} 个其他在线设备？`,
      '批量下线确认',
      { type: 'warning' },
    );
    sessionActioning.value = true;
    await Promise.all(
      otherActiveSessions.value.map((session) =>
        forceOffline(session.sessionId),
      ),
    );
    ElMessage.success('其他在线设备已下线');
    await loadSessions();
  } catch {
    // noop
  } finally {
    sessionActioning.value = false;
  }
}

function formatProfileDate(value?: null | string, placeholder = '-') {
  return formatDateTime(value, placeholder);
}

function formatSessionTime(value?: null | string) {
  return formatDateTime(value);
}

function formatDevice(raw?: null | string) {
  if (!raw) return 'Unknown';
  const ua = raw.toLowerCase();
  if (ua.includes('edg/')) return 'Microsoft Edge';
  if (ua.includes('chrome/')) return 'Google Chrome';
  if (ua.includes('firefox/')) return 'Mozilla Firefox';
  if (ua.includes('safari/') && !ua.includes('chrome/')) return 'Safari';
  if (ua.includes('java-http-client')) return 'Browser Session';
  return raw;
}

watch(cropScale, drawAvatarPreview);
onMounted(() => {
  if (!isForcedPasswordChange.value) {
    void loadProfile();
    void loadSessions();
  }
});
</script>

<template>
  <section class="personal-center-page">
    <div class="personal-hero">
      <div class="hero-profile">
        <ElAvatar
          :size="84"
          :src="profile?.avatarUrl || undefined"
          class="hero-avatar"
        >
          {{ avatarName }}
        </ElAvatar>
        <div class="hero-copy">
          <p class="eyebrow">Personal Center</p>
          <h1>个人中心</h1>
          <p class="summary">
            {{
              isForcedPasswordChange
                ? '当前会话处于受限改密态，请先完成密码更新。'
                : '集中维护个人资料、头像、密码与在线设备。'
            }}
          </p>
          <div class="hero-tags">
            <ElTag
              :type="profile?.enabled === false ? 'danger' : 'success'"
              effect="dark"
            >
              {{ profile?.enabled === false ? '账号停用' : '账号启用' }}
            </ElTag>
            <ElTag v-if="isForcedPasswordChange" type="warning" effect="dark">
              必须修改密码
            </ElTag>
            <ElTag v-else type="info" effect="plain">
              {{ profile?.tenantId || authStore.snapshot?.tenantId || '-' }}
            </ElTag>
          </div>
        </div>
      </div>
      <div class="hero-actions">
        <ElButton
          :loading="loading"
          :disabled="isForcedPasswordChange"
          @click="loadProfile"
        >
          刷新资料
        </ElButton>
        <ElButton
          type="primary"
          :disabled="isForcedPasswordChange"
          @click="openSessionDrawer"
        >
          在线设备
        </ElButton>
      </div>
    </div>

    <section class="overview-grid">
      <article class="overview-card">
        <span>资料完整度</span>
        <strong>{{ profileCompletion }}%</strong>
        <ElProgress :percentage="profileCompletion" :show-text="false" />
        <small>{{
          missingProfileItems.length > 0
            ? `待补充：${missingProfileItems.join('、')}`
            : '资料已完整'
        }}</small>
      </article>
      <article class="overview-card">
        <span>安全状态</span>
        <strong>{{ securityScore }}%</strong>
        <ElProgress
          :percentage="securityScore"
          :show-text="false"
          :status="securityScore >= 80 ? 'success' : undefined"
        />
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
      <ElCard class="profile-card" shadow="never">
        <template #header>
          <div class="card-head">
            <span>个人资料</span>
            <ElTag size="small" type="info" effect="plain">可编辑</ElTag>
          </div>
        </template>

        <ElSkeleton v-if="loading && !profile" :rows="8" animated />
        <template v-else>
          <div class="avatar-panel">
            <ElAvatar
              :size="96"
              :src="profile?.avatarUrl || undefined"
              class="profile-avatar"
            >
              {{ avatarName }}
            </ElAvatar>
            <div class="avatar-copy">
              <strong>{{
                profile?.displayName || profile?.username || '-'
              }}</strong>
              <span
                >{{ profile?.username || '-' }} ·
                {{ profile?.tenantId || '-' }}</span
              >
              <ElUpload
                :auto-upload="false"
                :show-file-list="false"
                accept="image/png,image/jpeg"
                :disabled="avatarUploadDisabled"
                :on-change="handleAvatarSelected"
              >
                <ElButton type="primary" plain :disabled="avatarUploadDisabled">
                  更换头像
                </ElButton>
              </ElUpload>
            </div>
          </div>

          <ElForm
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-position="top"
            class="profile-form"
          >
            <ElFormItem label="显示名称" prop="displayName">
              <ElInput
                v-model.trim="profileForm.displayName"
                maxlength="32"
                show-word-limit
                placeholder="用于页面展示"
                :disabled="profileFormDisabled"
              />
            </ElFormItem>
            <ElFormItem label="手机号" prop="mobile">
              <ElInput
                v-model.trim="profileForm.mobile"
                maxlength="11"
                placeholder="11 位手机号"
                :disabled="profileFormDisabled"
              />
            </ElFormItem>
            <ElFormItem label="邮箱" prop="email">
              <ElInput
                v-model.trim="profileForm.email"
                maxlength="128"
                placeholder="用于密码找回和通知"
                :disabled="profileFormDisabled"
              />
            </ElFormItem>
            <div class="form-actions">
              <ElButton
                :disabled="profileFormDisabled"
                @click="resetProfileForm"
              >
                重置
              </ElButton>
              <ElButton
                type="primary"
                :loading="profileSubmitting"
                :disabled="profileFormDisabled"
                @click="submitProfile"
              >
                保存资料
              </ElButton>
            </div>
          </ElForm>

          <ElAlert
            v-if="isForcedPasswordChange"
            class="inline-alert"
            type="warning"
            :closable="false"
            show-icon
            title="受限改密态下暂不允许修改个人资料，请先完成密码更新。"
          />
        </template>
      </ElCard>

      <div class="right-stack">
        <ElCard class="password-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>{{
                isForcedPasswordChange ? '完成强制改密' : '修改密码'
              }}</span>
              <ElTag
                :type="isForcedPasswordChange ? 'warning' : 'success'"
                size="small"
                effect="plain"
              >
                {{ isForcedPasswordChange ? '待处理' : '正常' }}
              </ElTag>
            </div>
          </template>

          <ElAlert
            v-if="isForcedPasswordChange"
            class="inline-alert"
            type="warning"
            :closable="false"
            show-icon
            title="为了保护账号安全，请先更新密码。"
          />

          <ElForm
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-position="top"
            @submit.prevent
          >
            <input
              class="password-form-username"
              autocomplete="username"
              name="username"
              readonly
              tabindex="-1"
              type="text"
              :value="passwordFormUsername"
            />
            <ElFormItem label="原密码" prop="oldPassword">
              <ElInput
                v-model="passwordForm.oldPassword"
                type="password"
                show-password
                autocomplete="current-password"
              />
            </ElFormItem>
            <ElFormItem label="新密码" prop="newPassword">
              <ElInput
                v-model="passwordForm.newPassword"
                type="password"
                show-password
                autocomplete="new-password"
              />
            </ElFormItem>
            <div class="password-strength">
              <div class="strength-head">
                <span>密码强度</span>
                <strong>{{ passwordStrength.label }}</strong>
              </div>
              <ElProgress
                :percentage="passwordStrength.score"
                :show-text="false"
                :status="passwordStrength.status"
              />
              <ul>
                <li
                  v-for="item in passwordChecks"
                  :key="item.label"
                  :class="{ passed: item.passed }"
                >
                  {{ item.label }}
                </li>
              </ul>
            </div>
            <ElFormItem label="确认新密码" prop="confirmPassword">
              <ElInput
                v-model="passwordForm.confirmPassword"
                type="password"
                show-password
                autocomplete="new-password"
              />
            </ElFormItem>
            <ElButton
              type="primary"
              :loading="passwordSubmitting"
              @click="submitPasswordChange"
            >
              更新密码
            </ElButton>
          </ElForm>
        </ElCard>

        <ElCard class="security-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span>账号与安全</span>
              <ElButton
                link
                type="primary"
                :disabled="isForcedPasswordChange"
                @click="openSessionDrawer"
              >
                管理设备
              </ElButton>
            </div>
          </template>

          <ElDescriptions :column="1" border>
            <ElDescriptionsItem label="用户 ID">
              {{ profile?.id || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="当前租户">
              {{ profile?.tenantId || authStore.snapshot?.tenantId || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="角色">
              <div class="role-list">
                <ElTag
                  v-for="role in roleTags"
                  :key="role"
                  size="small"
                  effect="plain"
                >
                  {{ role }}
                </ElTag>
                <span v-if="roleTags.length === 0">-</span>
              </div>
            </ElDescriptionsItem>
            <ElDescriptionsItem label="数据权限">
              {{ dataScopeLabel }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="密码更新时间">
              {{ formatProfileDate(profile?.passwordUpdatedAt) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="资料更新时间">
              {{ formatProfileDate(profile?.updatedAt) }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="账号创建时间">
              {{ formatProfileDate(profile?.createdAt) }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </ElCard>
      </div>
    </div>

    <ElCard class="timeline-card" shadow="never">
      <template #header>
        <div class="card-head">
          <span>账号动态</span>
          <ElTag size="small" effect="plain">当前账号</ElTag>
        </div>
      </template>
      <div class="timeline-grid">
        <article
          v-for="item in timelineItems"
          :key="item.title"
          class="timeline-item"
        >
          <span>{{ item.title }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.hint }}</small>
        </article>
      </div>
    </ElCard>

    <ElDialog
      v-model="cropVisible"
      title="裁剪头像"
      width="520px"
      destroy-on-close
    >
      <div class="crop-dialog">
        <canvas
          ref="canvasRef"
          class="avatar-canvas"
          width="320"
          height="320"
        ></canvas>
        <div class="crop-controls">
          <span>缩放</span>
          <ElSlider
            v-model="cropScale"
            :min="1"
            :max="2.5"
            :step="0.05"
            @input="drawAvatarPreview"
          />
        </div>
        <p class="crop-hint">
          头像会按正方形裁剪并上传，仅支持 PNG/JPEG，建议小于 2MB。
        </p>
      </div>
      <template #footer>
        <ElButton @click="cropVisible = false">取消</ElButton>
        <ElButton
          type="primary"
          :loading="avatarSubmitting"
          @click="submitAvatar"
        >
          上传头像
        </ElButton>
      </template>
    </ElDialog>

    <ElDrawer v-model="sessionDrawerVisible" title="在线设备" size="860px">
      <ElAlert
        title="可查看当前账号的在线会话，并将不再使用的设备强制下线。当前会话不能在这里下线。"
        type="info"
        show-icon
        :closable="false"
        class="inline-alert"
      />
      <div class="session-toolbar">
        <span
          >共 {{ sessionsList.length }} 个会话，{{
            activeSessionCount
          }}
          个在线</span
        >
        <div>
          <ElButton
            size="small"
            :loading="sessionsLoading"
            @click="loadSessions"
          >
            刷新
          </ElButton>
          <ElButton
            size="small"
            type="danger"
            plain
            :disabled="otherActiveSessions.length === 0"
            :loading="sessionActioning"
            @click="offlineOtherSessions"
          >
            下线其他设备
          </ElButton>
        </div>
      </div>
      <ElTable v-loading="sessionsLoading" :data="sessionsList" stripe>
        <ElTableColumn label="标记" width="90">
          <template #default="{ row }">
            <ElTag
              v-if="row.currentSession"
              type="success"
              effect="dark"
              size="small"
            >
              当前
            </ElTag>
            <ElTag v-else-if="row.active" type="info" size="small">
              在线
            </ElTag>
            <ElTag v-else type="info" effect="plain" size="small">离线</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="设备" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ formatDevice(row.device) }}</template>
        </ElTableColumn>
        <ElTableColumn prop="clientIp" label="登录 IP" width="140" />

        <ElTableColumn
          prop="loginLocation"
          label="登录地址"
          min-width="120"
          show-overflow-tooltip
        />
        <ElTableColumn label="首次登录" width="180">
          <template #default="{ row }">
            {{ formatSessionTime(row.issuedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="最后访问" width="180">
          <template #default="{ row }">
            {{ formatSessionTime(row.lastAccessAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="过期时间" width="180">
          <template #default="{ row }">
            {{ formatSessionTime(row.expiresAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <ElButton
              link
              type="danger"
              :disabled="!row.active || row.currentSession"
              @click="kickSession(row.sessionId)"
            >
              下线
            </ElButton>
          </template>
        </ElTableColumn>
        <template #empty>
          <ElEmpty description="暂无在线设备" />
        </template>
      </ElTable>
    </ElDrawer>
  </section>
</template>

<style scoped lang="scss">
.personal-center-page {
  min-height: calc(100vh - 140px);
  padding: 28px;
  background: linear-gradient(135deg, #f6f8fb 0%, #edf2f7 100%);
}

.personal-hero,
.overview-card,
.profile-card,
.password-card,
.security-card,
.timeline-card {
  border: 1px solid rgb(15 23 42 / 8%);
  border-radius: 20px;
}

.personal-hero {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 28px;
  margin-bottom: 22px;
  background: rgb(255 255 255 / 88%);
}

.hero-profile {
  display: flex;
  gap: 20px;
  align-items: center;
  min-width: 0;
}

.hero-copy {
  min-width: 0;
}

.eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 800;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.18em;
}

h1 {
  margin: 0;
  font-size: 32px;
  color: #0f172a;
}

.summary {
  max-width: 660px;
  margin: 10px 0 0;
  line-height: 1.7;
  color: #52657a;
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
  background: rgb(255 255 255 / 90%);
}

.overview-card span,
.timeline-item span {
  font-size: 13px;
  color: #64748b;
}

.overview-card strong {
  font-size: 24px;
  color: #0f172a;
}

.overview-card small,
.timeline-item small {
  color: #708090;
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

.card-head,
.session-toolbar,
.strength-head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.avatar-panel {
  display: flex;
  gap: 18px;
  align-items: center;
  padding: 18px;
  margin-bottom: 20px;
  background: linear-gradient(
    135deg,
    rgb(20 184 166 / 10%),
    rgb(14 165 233 / 8%)
  );
  border: 1px solid rgb(20 184 166 / 18%);
  border-radius: 18px;
}

.avatar-copy {
  display: grid;
  gap: 8px;
}

.profile-form {
  margin-top: 4px;
}

.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.inline-alert {
  margin-bottom: 18px;
}

.password-form-username {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  white-space: nowrap;
  border: 0;
  clip-path: inset(50%);
}

.password-strength {
  padding: 14px;
  margin: -4px 0 18px;
  background: #f8fafc;
  border: 1px solid rgb(15 23 42 / 8%);
  border-radius: 16px;
}

.password-strength ul {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
  padding: 0;
  margin: 12px 0 0;
  list-style: none;
}

.password-strength li {
  font-size: 13px;
  color: #94a3b8;
}

.password-strength li.passed {
  color: #0f766e;
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
  background: #f8fafc;
  border: 1px solid rgb(15 23 42 / 8%);
  border-radius: 16px;
}

.crop-dialog {
  display: grid;
  gap: 18px;
  justify-items: center;
}

.avatar-canvas {
  width: 320px;
  height: 320px;
  border-radius: 50%;
}

.crop-controls {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 12px;
  align-items: center;
  width: 100%;
}

.crop-hint {
  margin: 0;
  font-size: 13px;
  color: #64748b;
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
    flex-direction: column;
    align-items: stretch;
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
