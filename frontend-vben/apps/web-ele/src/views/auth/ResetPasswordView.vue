<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus';

import type { PasswordResetPolicy } from '#/api/auth-session';
import type {
  CaptchaStatus,
  CaptchaTrackPayload,
  CaptchaType,
} from '#/components/slider-captcha/types';

import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElResult,
} from 'element-plus';

import {
  confirmPasswordReset,
  requestPasswordReset,
  verifyPasswordResetToken,
} from '#/api/auth-session';
import { getCaptchaApi, verifyCaptchaApi } from '#/api/core/auth';
import SliderCaptcha from '#/components/slider-captcha/index.vue';
import PointCaptcha from '#/components/slider-captcha/point-captcha.vue';

type FormRuleCallback = (error?: Error) => void;
type TokenState =
  | 'completed'
  | 'error'
  | 'idle'
  | 'invalid'
  | 'valid'
  | 'verifying';

const DEFAULT_PASSWORD_POLICY: PasswordResetPolicy = {
  passwordMaxLength: 64,
  passwordMinLength: 8,
  passwordRequireLetter: true,
  passwordRequireNumber: true,
  passwordRequireSpecial: false,
};
const RESEND_COOLDOWN_SECONDS = 60;

const route = useRoute();
const router = useRouter();
const token = computed(() => String(route.query.token ?? '').trim());
const isResetLinkMode = computed(() => Boolean(token.value));

const loading = ref(false);
const requestSent = ref(false);
const requestMessage = ref('');
const resendSeconds = ref(0);
let cooldownTimer: number | undefined;

const tokenState = ref<TokenState>('idle');
const resetUsername = ref('');
const passwordPolicy = ref<PasswordResetPolicy>({
  ...DEFAULT_PASSWORD_POLICY,
});

const requestFormRef = ref<FormInstance>();
const confirmFormRef = ref<FormInstance>();
const requestForm = reactive({ email: '', username: '' });
const confirmForm = reactive({ confirmPassword: '', newPassword: '' });

const requestRules: FormRules = {
  email: [
    { message: '请输入绑定邮箱', required: true, trigger: 'blur' },
    { message: '请输入有效的邮箱地址', type: 'email', trigger: 'blur' },
  ],
  username: [
    { message: '请输入用户名', required: true, trigger: 'blur' },
    { max: 16, message: '用户名长度需为 5-16 位', min: 5, trigger: 'blur' },
  ],
};

function hasLetter(value: string) {
  return /\p{L}/u.test(value);
}

function hasNumber(value: string) {
  return /\p{N}/u.test(value);
}

function hasSpecial(value: string) {
  return /[^\p{L}\p{N}\s]/u.test(value);
}

function validateNewPassword(
  _rule: unknown,
  value: string,
  callback: FormRuleCallback,
) {
  const policy = passwordPolicy.value;
  if (!value) {
    callback(new Error('请输入新密码'));
  } else if (
    value.length < policy.passwordMinLength ||
    value.length > policy.passwordMaxLength
  ) {
    callback(
      new Error(
        `密码长度需在 ${policy.passwordMinLength}-${policy.passwordMaxLength} 位之间`,
      ),
    );
  } else if (/\s/u.test(value)) {
    callback(new Error('密码不能包含空白字符'));
  } else if (policy.passwordRequireLetter && !hasLetter(value)) {
    callback(new Error('密码必须包含字母'));
  } else if (policy.passwordRequireNumber && !hasNumber(value)) {
    callback(new Error('密码必须包含数字'));
  } else if (policy.passwordRequireSpecial && !hasSpecial(value)) {
    callback(new Error('密码必须包含特殊字符'));
  } else {
    if (confirmForm.confirmPassword) {
      void confirmFormRef.value?.validateField('confirmPassword');
    }
    callback();
  }
}

function validateConfirmPassword(
  _rule: unknown,
  value: string,
  callback: FormRuleCallback,
) {
  if (!value) {
    callback(new Error('请再次输入新密码'));
  } else if (value === confirmForm.newPassword) {
    callback();
  } else {
    callback(new Error('两次输入的新密码不一致'));
  }
}

const confirmRules: FormRules = {
  confirmPassword: [
    { trigger: ['blur', 'change'], validator: validateConfirmPassword },
  ],
  newPassword: [
    { trigger: ['blur', 'change'], validator: validateNewPassword },
  ],
};

const passwordRequirements = computed(() => {
  const value = confirmForm.newPassword;
  const policy = passwordPolicy.value;
  return [
    {
      label: `${policy.passwordMinLength}-${policy.passwordMaxLength} 位`,
      met:
        value.length >= policy.passwordMinLength &&
        value.length <= policy.passwordMaxLength,
    },
    ...(policy.passwordRequireLetter
      ? [{ label: '包含字母', met: hasLetter(value) }]
      : []),
    ...(policy.passwordRequireNumber
      ? [{ label: '包含数字', met: hasNumber(value) }]
      : []),
    ...(policy.passwordRequireSpecial
      ? [{ label: '包含特殊字符', met: hasSpecial(value) }]
      : []),
    { label: '不含空格', met: Boolean(value) && !/\s/u.test(value) },
  ];
});

const captchaDialogVisible = ref(false);
const captchaLoading = ref(false);
const captchaStatus = ref<CaptchaStatus>('ready');
const captchaType = ref<CaptchaType>('SLIDER');
const captchaToken = ref('');
let currentCaptchaId = '';
const isPointCaptcha = computed(() => captchaType.value === 'WORD_IMAGE_CLICK');
const captcha = reactive({
  background: '',
  bgHeight: 0,
  bgWidth: 0,
  slider: '',
  sliderHeight: 0,
  sliderWidth: 0,
});

function toDataUrl(image: string | undefined, mime: string) {
  if (!image) return '';
  return image.startsWith('data:') ? image : `data:${mime};base64,${image}`;
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function resetCaptchaState() {
  captchaToken.value = '';
  currentCaptchaId = '';
  captchaStatus.value = 'ready';
}

async function reloadCaptcha() {
  captchaLoading.value = true;
  resetCaptchaState();
  try {
    const result = await getCaptchaApi(requestForm.username || undefined);
    currentCaptchaId = result?.captchaId ?? '';
    captchaType.value =
      result?.type === 'WORD_IMAGE_CLICK' ? 'WORD_IMAGE_CLICK' : 'SLIDER';
    captcha.background = toDataUrl(result?.backgroundImage, 'image/jpeg');
    captcha.slider = toDataUrl(result?.sliderImage, 'image/png');
    captcha.bgWidth = result?.backgroundImageWidth ?? 0;
    captcha.bgHeight = result?.backgroundImageHeight ?? 0;
    captcha.sliderWidth = result?.sliderImageWidth ?? 0;
    captcha.sliderHeight = result?.sliderImageHeight ?? 0;
  } finally {
    captchaLoading.value = false;
  }
}

async function openCaptchaDialog() {
  captchaDialogVisible.value = true;
  try {
    await reloadCaptcha();
  } catch {
    captchaDialogVisible.value = false;
  }
}

async function submitRequest() {
  if (loading.value || requestSent.value) return;
  const valid = await requestFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  await openCaptchaDialog();
}

async function handleVerify(trackPayload: CaptchaTrackPayload) {
  captchaStatus.value = 'verifying';
  try {
    const verification = await verifyCaptchaApi(
      currentCaptchaId,
      JSON.stringify(trackPayload),
    );
    captchaToken.value = verification.token;
    captchaStatus.value = 'success';
    await delay(420);
  } catch {
    resetCaptchaState();
    captchaStatus.value = 'error';
    await delay(700);
    await reloadCaptcha();
    return;
  }

  captchaDialogVisible.value = false;
  captchaStatus.value = 'ready';
  await sendResetRequest().catch(() => {});
}

async function sendResetRequest() {
  if (!captchaToken.value || loading.value) return;
  loading.value = true;
  try {
    const result = await requestPasswordReset({
      captchaId: captchaToken.value,
      email: requestForm.email.trim(),
      username: requestForm.username.trim(),
    });
    requestMessage.value = result.message;
    requestSent.value = true;
    startResendCooldown();
  } finally {
    resetCaptchaState();
    loading.value = false;
  }
}

function startResendCooldown() {
  stopResendCooldown();
  resendSeconds.value = RESEND_COOLDOWN_SECONDS;
  cooldownTimer = window.setInterval(() => {
    resendSeconds.value -= 1;
    if (resendSeconds.value <= 0) {
      stopResendCooldown();
    }
  }, 1000);
}

function stopResendCooldown() {
  if (cooldownTimer !== undefined) {
    window.clearInterval(cooldownTimer);
    cooldownTimer = undefined;
  }
}

function prepareAnotherRequest() {
  if (resendSeconds.value > 0) return;
  requestSent.value = false;
  requestMessage.value = '';
  resetCaptchaState();
}

async function verifyResetLink() {
  if (!token.value) {
    tokenState.value = 'idle';
    return;
  }

  tokenState.value = 'verifying';
  resetUsername.value = '';
  try {
    const result = await verifyPasswordResetToken(token.value);
    if (!result.valid) {
      tokenState.value = 'invalid';
      return;
    }
    resetUsername.value = result.username ?? '';
    passwordPolicy.value = result.passwordPolicy ?? {
      ...DEFAULT_PASSWORD_POLICY,
    };
    tokenState.value = 'valid';
  } catch {
    tokenState.value = 'error';
  }
}

async function submitConfirm() {
  if (loading.value || tokenState.value !== 'valid') return;
  const valid = await confirmFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    const result = await confirmPasswordReset({
      newPassword: confirmForm.newPassword,
      token: token.value,
    });
    tokenState.value = 'completed';
    ElMessage.success(result.message);
    confirmForm.newPassword = '';
    confirmForm.confirmPassword = '';
  } catch (error: any) {
    if (error?.response?.data?.code === 'PASSWORD_RESET_TOKEN_INVALID') {
      tokenState.value = 'invalid';
    }
  } finally {
    loading.value = false;
  }
}

function restartRequest() {
  void router.replace({ path: '/reset-password', query: {} });
}

function goToLogin() {
  void router.push('/login');
}

watch(
  token,
  () => {
    void verifyResetLink();
  },
  { immediate: true },
);

onBeforeUnmount(stopResendCooldown);
</script>

<template>
  <div class="reset-view">
    <div class="auth-title">
      <h2>{{ isResetLinkMode ? '设置新密码' : '忘记密码' }}</h2>
      <p>
        {{
          isResetLinkMode
            ? '验证重置链接后即可设置新密码'
            : '使用用户名和绑定邮箱找回账号'
        }}
      </p>
    </div>

    <template v-if="!isResetLinkMode">
      <ElResult
        v-if="requestSent"
        icon="success"
        title="重置申请已受理"
        :sub-title="requestMessage || '如果账号信息匹配，重置邮件会很快送达'"
      >
        <template #extra>
          <div class="result-actions">
            <ElButton type="primary" @click="goToLogin">返回登录</ElButton>
            <ElButton
              :disabled="resendSeconds > 0"
              @click="prepareAnotherRequest"
            >
              {{
                resendSeconds > 0
                  ? `${resendSeconds} 秒后可重新申请`
                  : '重新申请'
              }}
            </ElButton>
          </div>
        </template>
      </ElResult>

      <ElForm
        v-else
        ref="requestFormRef"
        class="auth-form"
        :model="requestForm"
        :rules="requestRules"
        label-width="0"
        @submit.prevent="submitRequest"
      >
        <ElFormItem prop="username">
          <ElInput
            v-model.trim="requestForm.username"
            autocomplete="username"
            maxlength="16"
            placeholder="用户名（5-16 位）"
            size="large"
          />
        </ElFormItem>

        <ElFormItem prop="email">
          <ElInput
            v-model.trim="requestForm.email"
            autocomplete="email"
            maxlength="128"
            placeholder="绑定邮箱"
            size="large"
            type="email"
          />
        </ElFormItem>

        <ElFormItem>
          <ElButton
            :loading="loading || captchaLoading"
            class="w-full"
            native-type="submit"
            size="large"
            type="primary"
          >
            验证并发送重置邮件
          </ElButton>
        </ElFormItem>

        <ElFormItem>
          <ElButton class="w-full" size="large" @click="goToLogin">
            返回登录
          </ElButton>
        </ElFormItem>
      </ElForm>
    </template>

    <template v-else>
      <ElResult
        v-if="tokenState === 'verifying'"
        icon="info"
        title="正在校验重置链接"
        sub-title="请稍候"
      />

      <ElResult
        v-else-if="tokenState === 'error'"
        icon="warning"
        title="暂时无法校验链接"
        sub-title="请检查网络后重试"
      >
        <template #extra>
          <div class="result-actions">
            <ElButton type="primary" @click="verifyResetLink">
              重新校验
            </ElButton>
            <ElButton @click="goToLogin">返回登录</ElButton>
          </div>
        </template>
      </ElResult>

      <ElResult
        v-else-if="tokenState === 'invalid'"
        icon="error"
        title="重置链接无效或已过期"
        sub-title="请重新发起密码重置申请"
      >
        <template #extra>
          <div class="result-actions">
            <ElButton type="primary" @click="restartRequest">
              重新申请
            </ElButton>
            <ElButton @click="goToLogin">返回登录</ElButton>
          </div>
        </template>
      </ElResult>

      <ElResult
        v-else-if="tokenState === 'completed'"
        icon="success"
        title="密码重置成功"
        sub-title="所有旧会话已失效，请使用新密码重新登录"
      >
        <template #extra>
          <ElButton type="primary" @click="goToLogin">前往登录</ElButton>
        </template>
      </ElResult>

      <template v-else-if="tokenState === 'valid'">
        <ElAlert
          :closable="false"
          show-icon
          :title="`正在为 ${resetUsername || '当前账号'} 设置新密码`"
          type="success"
        />

        <ElForm
          ref="confirmFormRef"
          class="auth-form confirm-form"
          :model="confirmForm"
          :rules="confirmRules"
          label-width="0"
          @submit.prevent="submitConfirm"
        >
          <ElFormItem prop="newPassword">
            <ElInput
              v-model="confirmForm.newPassword"
              autocomplete="new-password"
              :maxlength="passwordPolicy.passwordMaxLength"
              placeholder="新密码"
              show-password
              size="large"
              type="password"
            />
          </ElFormItem>

          <div class="password-requirements">
            <span
              v-for="item in passwordRequirements"
              :key="item.label"
              :class="{ met: item.met }"
            >
              {{ item.label }}
            </span>
          </div>

          <ElFormItem prop="confirmPassword">
            <ElInput
              v-model="confirmForm.confirmPassword"
              autocomplete="new-password"
              :maxlength="passwordPolicy.passwordMaxLength"
              placeholder="确认新密码"
              show-password
              size="large"
              type="password"
            />
          </ElFormItem>

          <ElFormItem>
            <ElButton
              :loading="loading"
              class="w-full"
              native-type="submit"
              size="large"
              type="primary"
            >
              确认重置密码
            </ElButton>
          </ElFormItem>

          <ElFormItem>
            <ElButton class="w-full" size="large" @click="goToLogin">
              返回登录
            </ElButton>
          </ElFormItem>
        </ElForm>
      </template>
    </template>

    <ElDialog
      v-model="captchaDialogVisible"
      align-center
      append-to-body
      :close-on-click-modal="false"
      destroy-on-close
      :show-close="captchaStatus === 'ready' || captchaStatus === 'error'"
      title="安全验证"
      width="min(420px, calc(100vw - 32px))"
    >
      <div class="captcha-dialog__body">
        <p class="captcha-dialog__tip">
          {{
            isPointCaptcha
              ? '请按提示依次点击文字完成安全确认'
              : '拖动拼图完成安全确认'
          }}
        </p>
        <template v-if="!captchaLoading && captcha.background">
          <PointCaptcha
            v-if="isPointCaptcha"
            :background-height="captcha.bgHeight"
            :background-image="captcha.background"
            :background-width="captcha.bgWidth"
            :status="captchaStatus"
            :tip-image="captcha.slider"
            @refresh="reloadCaptcha"
            @verify="handleVerify"
          />
          <SliderCaptcha
            v-else-if="captcha.slider"
            :background-height="captcha.bgHeight"
            :background-image="captcha.background"
            :background-width="captcha.bgWidth"
            :slider-height="captcha.sliderHeight"
            :slider-image="captcha.slider"
            :slider-width="captcha.sliderWidth"
            :status="captchaStatus"
            @refresh="reloadCaptcha"
            @verify="handleVerify"
          />
        </template>
        <div v-else class="captcha-dialog__loading">验证码加载中…</div>
      </div>
    </ElDialog>
  </div>
</template>

<style scoped>
.reset-view {
  width: 100%;
}

.auth-title {
  margin-bottom: 28px;
}

.auth-title h2 {
  margin: 0 0 12px;
  font-size: 28px;
  font-weight: 700;
  line-height: 1.25;
  color: var(--el-text-color-primary);
}

.auth-title p {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.auth-form :deep(.el-form-item__label) {
  display: none;
}

.auth-form :deep(.el-input__inner) {
  --el-input-inner-height: 44px;
}

.confirm-form {
  margin-top: 20px;
}

.password-requirements {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: -8px 0 18px;
}

.password-requirements span {
  padding: 4px 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.password-requirements span.met {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-7);
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.captcha-dialog__body {
  padding: 4px 4px 8px;
}

.captcha-dialog__tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.captcha-dialog__loading {
  display: grid;
  place-items: center;
  min-height: 200px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
