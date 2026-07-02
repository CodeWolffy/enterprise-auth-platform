<script setup lang="ts">
import type { FormInstance } from 'element-plus';

import type { CaptchaTrackPayload } from '#/components/slider-captcha/types';

import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
} from 'element-plus';

import {
  confirmPasswordReset,
  requestPasswordReset,
  verifyPasswordResetToken,
} from '#/api/auth-session';
import { getCaptchaApi, verifyCaptchaApi } from '#/api/core/auth';
import SliderCaptcha from '#/components/slider-captcha/index.vue';

const route = useRoute();
const router = useRouter();

// 有 token 时进入「设置新密码」模式，否则进入「申请重置邮件」模式
const token = ref(String(route.query.token ?? ''));
const tokenValid = ref(false);
const verifying = ref(false);
const resetUsername = ref('');
const loading = ref(false);

const requestFormRef = ref<FormInstance>();
const confirmFormRef = ref<FormInstance>();

const requestForm = reactive({ username: '', email: '' });
const requestRules = {
  username: [
    { message: '请输入用户名', required: true, trigger: 'blur' },
    { max: 16, message: '用户名长度需为 5-16 位', min: 5, trigger: 'blur' },
  ],
  email: [
    { message: '请输入绑定邮箱', required: true, trigger: 'blur' },
    { message: '请输入有效的邮箱地址', type: 'email', trigger: 'blur' },
  ],
};

const confirmForm = reactive({ newPassword: '', confirmPassword: '' });
const validateNewPwd = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error('请输入新密码'));
  } else if (value.length < 8) {
    callback(new Error('新密码至少 8 位'));
  } else {
    if (confirmForm.confirmPassword) {
      confirmFormRef.value?.validateField('confirmPassword');
    }
    callback();
  }
};
const validateConfirmPwd = (_rule: any, value: string, callback: any) => {
  if (!value) {
    callback(new Error('请再次输入新密码'));
  } else if (value === confirmForm.newPassword) {
    callback();
  } else {
    callback(new Error('两次输入的新密码不一致'));
  }
};
const confirmRules = {
  newPassword: [{ validator: validateNewPwd, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirmPwd, trigger: 'blur' }],
};

// 滑块验证码状态（仅申请流程使用）
const captchaDialogVisible = ref(false);
const captchaLoading = ref(false);
const captchaVerifying = ref(false);
const captchaVerified = ref(false);
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
  captchaVerifying.value = true;
  try {
    const verification = await verifyCaptchaApi(currentCaptchaId, code);
    captchaToken.value = verification.token;
    captchaVerified.value = true;
    captchaDialogVisible.value = false;
  } catch {
    captchaVerified.value = false;
    captchaToken.value = '';
    await reloadCaptcha();
  } finally {
    captchaVerifying.value = false;
  }
}

async function submitRequest() {
  const valid = await requestFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  if (!captchaVerified.value || !captchaToken.value) {
    ElMessage.warning('请先完成滑块验证');
    await openCaptchaDialog();
    return;
  }
  loading.value = true;
  try {
    const result: any = await requestPasswordReset({
      username: requestForm.username,
      email: requestForm.email,
      captchaId: captchaToken.value,
    });
    if (result?.result === 'NOT_FOUND') {
      ElMessage.error(result.message || '用户名不存在');
    } else if (result?.result === 'EMAIL_NOT_CONFIGURED') {
      ElMessage.error(result.message || '该账号未绑定邮箱，无法通过邮件重置');
    } else {
      ElMessage.success(
        result?.message || '如果账号存在且邮箱匹配，将会收到密码重置邮件',
      );
    }
  } catch {
    captchaVerified.value = false;
    captchaToken.value = '';
  } finally {
    loading.value = false;
  }
}

async function submitConfirm() {
  const valid = await confirmFormRef.value?.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    const result: any = await confirmPasswordReset({
      token: token.value,
      newPassword: confirmForm.newPassword,
    });
    ElMessage.success(result?.message || '密码已重置，请使用新密码登录');
    window.setTimeout(() => router.replace('/login'), 800);
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  if (!token.value) return;
  verifying.value = true;
  try {
    const result: any = await verifyPasswordResetToken(token.value);
    tokenValid.value = Boolean(result?.valid);
    resetUsername.value = result?.username || '';
  } catch {
    tokenValid.value = false;
  } finally {
    verifying.value = false;
  }
});
</script>

<template>
  <div class="reset-view">
    <div class="auth-title">
      <h2>{{ token ? '设置新密码' : '重置密码' }}</h2>
      <p>
        {{
          token
            ? '请设置新密码，完成后使用新密码登录'
            : '输入用户名与绑定邮箱，如信息匹配将收到重置链接'
        }}
      </p>
    </div>

    <!-- 申请重置邮件 -->
    <ElForm
      v-if="!token"
      ref="requestFormRef"
      class="auth-form"
      :model="requestForm"
      :rules="requestRules"
      label-width="0"
    >
      <ElFormItem prop="username">
        <ElInput
          v-model.trim="requestForm.username"
          maxlength="16"
          placeholder="用户名（5-16 位）"
          size="large"
        />
      </ElFormItem>

      <ElFormItem prop="email">
        <ElInput
          v-model.trim="requestForm.email"
          placeholder="绑定邮箱"
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
        <ElButton
          :loading="loading"
          class="w-full"
          size="large"
          type="primary"
          @click="submitRequest"
        >
          发送重置邮件
        </ElButton>
      </ElFormItem>

      <ElFormItem>
        <ElButton class="w-full" size="large" @click="router.push('/login')">
          返回登录
        </ElButton>
      </ElFormItem>
    </ElForm>

    <!-- 设置新密码 -->
    <div v-else>
      <ElAlert
        v-if="verifying"
        :closable="false"
        title="正在校验重置链接…"
        type="info"
      />

      <template v-else>
        <ElAlert
          v-if="!tokenValid"
          :closable="false"
          show-icon
          title="重置链接无效或已过期，请重新发起申请"
          type="error"
        />

        <ElAlert
          v-else
          :closable="false"
          show-icon
          :title="`链接已验证${resetUsername ? `：${resetUsername}` : ''}`"
          type="success"
        />

        <ElForm
          ref="confirmFormRef"
          class="auth-form"
          :model="confirmForm"
          :rules="confirmRules"
          label-width="0"
          style="margin-top: 16px"
        >
          <ElFormItem prop="newPassword">
            <ElInput
              v-model="confirmForm.newPassword"
              :disabled="!tokenValid"
              autocomplete="new-password"
              placeholder="新密码（至少 8 位）"
              show-password
              size="large"
              type="password"
            />
          </ElFormItem>

          <ElFormItem prop="confirmPassword">
            <ElInput
              v-model="confirmForm.confirmPassword"
              :disabled="!tokenValid"
              autocomplete="new-password"
              placeholder="确认新密码"
              show-password
              size="large"
              type="password"
            />
          </ElFormItem>

          <ElFormItem>
            <ElButton
              :disabled="!tokenValid"
              :loading="loading"
              class="w-full"
              size="large"
              type="primary"
              @click="submitConfirm"
            >
              确认重置密码
            </ElButton>
          </ElFormItem>

          <ElFormItem>
            <ElButton
              class="w-full"
              size="large"
              @click="router.push('/login')"
            >
              返回登录
            </ElButton>
          </ElFormItem>
        </ElForm>
      </template>
    </div>

    <div class="auth-footer">
      想起密码？
      <span class="auth-link" @click="router.push('/login')">去登录</span>
    </div>

    <ElDialog
      v-model="captchaDialogVisible"
      align-center
      :close-on-click-modal="false"
      destroy-on-close
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
          :verifying="captchaVerifying"
          @refresh="reloadCaptcha"
          @verify="handleVerify"
        />
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
