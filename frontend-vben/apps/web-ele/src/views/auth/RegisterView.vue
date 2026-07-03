<script setup lang="ts">
import type { FormInstance } from 'element-plus';

import type {
  CaptchaStatus,
  CaptchaTrackPayload,
} from '#/components/slider-captcha/types';

import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  ElButton,
  ElCheckbox,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

import { fetchRegisterOptions, registerApi } from '#/api/auth-session';
import { getCaptchaApi, verifyCaptchaApi } from '#/api/core/auth';
import SliderCaptcha from '#/components/slider-captcha/index.vue';

const router = useRouter();
const formRef = ref<FormInstance>();
const loading = ref(false);

const validatePassword = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error('请输入密码'));
  } else if (/^(?=.*[A-Z])(?=.*\d)[A-Z\d]{8,}$/i.test(value)) {
    if (state.form.confirmPassword) {
      formRef.value?.validateField('confirmPassword');
    }
    callback();
  } else {
    callback(new Error('密码至少 8 位，且需包含字母和数字'));
  }
};

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error('请再次输入密码'));
  } else if (value === state.form.password) {
    callback();
  } else {
    callback(new Error('两次输入的密码不一致'));
  }
};

const state = reactive({
  form: {
    username: '',
    displayName: '',
    password: '',
    confirmPassword: '',
    mobile: '',
    email: '',
    acceptedTerms: false,
  },
  rules: {
    username: [
      { message: '请输入用户名', required: true, trigger: 'blur' },
      {
        max: 16,
        message: '用户名长度需为 5-16 位',
        min: 5,
        trigger: 'blur',
      },
    ],
    displayName: [
      { message: '请输入显示名称', required: true, trigger: 'blur' },
      {
        max: 100,
        message: '显示名称长度需为 2-100 位',
        min: 2,
        trigger: 'blur',
      },
    ],
    password: [{ validator: validatePassword, trigger: 'blur' }],
    confirmPassword: [{ validator: validateConfirm, trigger: 'blur' }],
    mobile: [
      {
        message: '请输入正确的 11 位手机号',
        pattern: /^1\d{10}$/,
        trigger: 'blur',
      },
    ],
    email: [
      { message: '请输入正确的邮箱地址', type: 'email', trigger: 'blur' },
    ],
  },
});

// 滑块验证码状态
const captchaDialogVisible = ref(false);
const captchaLoading = ref(false);
const captchaStatus = ref<CaptchaStatus>('ready');
const captchaVerified = ref(false);
// 二次校验通过后返回的 token，注册接口以此作为 captchaId 下发
const captchaToken = ref('');
let currentCaptchaId = '';
const captcha = reactive({
  background: '',
  slider: '',
  bgWidth: 0,
  bgHeight: 0,
  sliderWidth: 0,
  sliderHeight: 0,
});

function toDataUrl(image: string | undefined, mime: string) {
  if (!image) return '';
  return image.startsWith('data:') ? image : `data:${mime};base64,${image}`;
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function reloadCaptcha() {
  captchaLoading.value = true;
  captchaVerified.value = false;
  captchaToken.value = '';
  try {
    const c = await getCaptchaApi();
    currentCaptchaId = c?.captchaId ?? '';
    captcha.background = toDataUrl(c?.backgroundImage, 'image/jpeg');
    captcha.slider = toDataUrl(c?.sliderImage, 'image/png');
    captcha.bgWidth = c?.backgroundImageWidth ?? 0;
    captcha.bgHeight = c?.backgroundImageHeight ?? 0;
    captcha.sliderWidth = c?.sliderImageWidth ?? 0;
    captcha.sliderHeight = c?.sliderImageHeight ?? 0;
  } finally {
    captchaLoading.value = false;
  }
}

async function openCaptchaDialog() {
  await reloadCaptcha();
  captchaDialogVisible.value = true;
}

async function handleVerify(trackPayload: CaptchaTrackPayload) {
  const code = JSON.stringify(trackPayload);
  captchaStatus.value = 'verifying';
  try {
    const verification = await verifyCaptchaApi(currentCaptchaId, code);
    captchaToken.value = verification.token;
    captchaVerified.value = true;
    // 校验通过：短暂展示成功态再关闭弹窗
    captchaStatus.value = 'success';
    await delay(420);
    captchaDialogVisible.value = false;
    captchaStatus.value = 'ready';
  } catch {
    // 校验失败：红色反馈后自动换一张
    captchaVerified.value = false;
    captchaToken.value = '';
    captchaStatus.value = 'error';
    await delay(700);
    await reloadCaptcha();
    captchaStatus.value = 'ready';
  }
}

function resetCaptchaState() {
  captchaVerified.value = false;
  captchaToken.value = '';
  currentCaptchaId = '';
}

async function submit() {
  if (loading.value) return;
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (!state.form.acceptedTerms) {
    ElMessage.warning('请先同意平台服务条款与注册规则');
    return;
  }
  if (!captchaVerified.value || !captchaToken.value) {
    ElMessage.warning('请先完成滑块验证');
    await openCaptchaDialog();
    return;
  }

  loading.value = true;
  try {
    await registerApi({
      username: state.form.username.trim(),
      displayName: state.form.displayName.trim(),
      password: state.form.password,
      mobile: state.form.mobile.trim() || undefined,
      email: state.form.email.trim() || undefined,
      captchaId: captchaToken.value,
    });
    ElMessage.success('账号创建成功，正在跳转登录页…');
    window.setTimeout(() => router.push('/login'), 600);
  } catch {
    // 注册失败后验证码已失效，需重新验证
    resetCaptchaState();
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  // 预取注册选项（默认租户/角色），失败时忽略不影响注册
  void fetchRegisterOptions().catch(() => {});
});
</script>

<template>
  <div class="register-view">
    <div class="auth-title">
      <h2>创建账号</h2>
      <p>注册后即可登录平台</p>
    </div>

    <ElForm
      ref="formRef"
      class="auth-form"
      :model="state.form"
      :rules="state.rules"
      label-width="0"
    >
      <ElFormItem prop="username">
        <ElInput
          v-model.trim="state.form.username"
          maxlength="16"
          placeholder="用户名（5-16 位）"
          size="large"
        />
      </ElFormItem>

      <ElFormItem prop="displayName">
        <ElInput
          v-model.trim="state.form.displayName"
          maxlength="100"
          placeholder="显示名称"
          size="large"
        />
      </ElFormItem>

      <ElFormItem prop="password">
        <ElInput
          v-model="state.form.password"
          autocomplete="new-password"
          placeholder="密码（至少 8 位，含字母和数字）"
          show-password
          size="large"
          type="password"
        />
      </ElFormItem>

      <ElFormItem prop="confirmPassword">
        <ElInput
          v-model="state.form.confirmPassword"
          autocomplete="new-password"
          placeholder="确认密码"
          show-password
          size="large"
          type="password"
        />
      </ElFormItem>

      <ElFormItem prop="mobile">
        <ElInput
          v-model.trim="state.form.mobile"
          maxlength="11"
          placeholder="手机号（选填）"
          size="large"
        />
      </ElFormItem>

      <ElFormItem prop="email">
        <ElInput
          v-model.trim="state.form.email"
          placeholder="邮箱（选填）"
          size="large"
        />
      </ElFormItem>

      <ElFormItem>
        <ElButton
          :loading="captchaLoading"
          :type="captchaVerified ? 'success' : 'primary'"
          class="captcha-button"
          plain
          size="large"
          @click="openCaptchaDialog"
        >
          {{ captchaVerified ? '验证通过，点击重新验证' : '点击完成滑块验证' }}
        </ElButton>
      </ElFormItem>

      <ElFormItem>
        <ElCheckbox v-model="state.form.acceptedTerms">
          我已阅读并同意平台服务条款与注册规则
        </ElCheckbox>
      </ElFormItem>

      <ElFormItem>
        <ElButton
          :loading="loading"
          class="w-full"
          size="large"
          type="primary"
          @click="submit"
        >
          创建账号
        </ElButton>
      </ElFormItem>
    </ElForm>

    <div class="auth-footer">
      已有账号？
      <span class="auth-link" @click="router.push('/login')">去登录</span>
    </div>

    <ElDialog
      v-model="captchaDialogVisible"
      align-center
      :close-on-click-modal="false"
      destroy-on-close
      :show-close="captchaStatus === 'ready' || captchaStatus === 'error'"
      title="安全验证"
      width="min(420px, calc(100vw - 32px))"
    >
      <div class="captcha-dialog__body">
        <p class="captcha-dialog__tip">拖动拼图完成本次安全确认</p>
        <SliderCaptcha
          v-if="!captchaLoading && captcha.background && captcha.slider"
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
        <div v-else class="captcha-dialog__loading">验证码加载中…</div>
      </div>
    </ElDialog>
  </div>
</template>

<style scoped>
.register-view {
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

.captcha-button {
  width: 100%;
}

.auth-footer {
  margin-top: 8px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.auth-link {
  margin-left: 4px;
  color: var(--el-color-primary);
  cursor: pointer;
}

.auth-link:hover {
  color: var(--el-color-primary-light-3);
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
