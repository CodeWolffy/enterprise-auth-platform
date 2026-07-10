<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import type {
  CaptchaStatus,
  CaptchaTrackPayload,
  CaptchaType,
} from '#/components/slider-captcha/types';

import { computed, reactive, ref } from 'vue';

import { AuthenticationLogin, z } from '@vben/common-ui';
import { $t } from '@vben/locales';

import { ElDialog, ElMessage } from 'element-plus';

import { getCaptchaApi, verifyCaptchaApi } from '#/api/core/auth';
import SliderCaptcha from '#/components/slider-captcha/index.vue';
import PointCaptcha from '#/components/slider-captcha/point-captcha.vue';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();

// 登录提交载荷（对齐后端 LoginRequest）
const loginUser = reactive({
  username: '',
  password: '',
  captchaId: '',
  captchaCode: '',
  device: navigator.userAgent,
});

const captchaDialogVisible = ref(false);
const captchaLoading = ref(false);
const captchaStatus = ref<CaptchaStatus>('ready');
const captchaType = ref<CaptchaType>('SLIDER');
const isPointCaptcha = computed(() => captchaType.value === 'WORD_IMAGE_CLICK');
const captcha = reactive({
  background: '',
  slider: '',
  bgWidth: 0,
  bgHeight: 0,
  sliderWidth: 0,
  sliderHeight: 0,
});

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z.string().min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
    },
  ];
});

function toDataUrl(image: string | undefined, mime: string) {
  if (!image) {
    return '';
  }
  return image.startsWith('data:') ? image : `data:${mime};base64,${image}`;
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

async function reloadCaptcha() {
  captchaLoading.value = true;
  try {
    // 传入用户名：后端据其近期失败情况决定下发滑块或文字点选(风险升级)
    const c = await getCaptchaApi(loginUser.username || undefined);
    loginUser.captchaId = c?.captchaId ?? '';
    captchaType.value =
      c?.type === 'WORD_IMAGE_CLICK' ? 'WORD_IMAGE_CLICK' : 'SLIDER';
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

// 账号密码校验通过后，弹出滑块验证
async function handleSubmit(val: Record<string, any>) {
  loginUser.username = val.username;
  loginUser.password = val.password;
  // 如果已有验证码token，直接尝试登录，无需重新验证
  if (loginUser.captchaCode) {
    await doLogin();
    return;
  }
  loginUser.captchaCode = '';
  await reloadCaptcha();
  captchaDialogVisible.value = true;
}

// 滑块拖动完成 -> 校验轨迹 -> 登录
async function handleVerify(trackPayload: CaptchaTrackPayload) {
  const code = JSON.stringify(trackPayload);
  captchaStatus.value = 'verifying';
  try {
    const verification = await verifyCaptchaApi(loginUser.captchaId, code);
    loginUser.captchaCode = verification.token;
  } catch {
    // 校验失败：红色反馈后自动换一张
    loginUser.captchaCode = '';
    captchaStatus.value = 'error';
    await delay(700);
    await reloadCaptcha();
    captchaStatus.value = 'ready';
    return;
  }

  // 校验通过：短暂展示成功态再关闭弹窗
  captchaStatus.value = 'success';
  await delay(420);
  captchaDialogVisible.value = false;
  captchaStatus.value = 'ready';

  await doLogin();
}

// 执行登录请求
async function doLogin() {
  try {
    await authStore.authLogin(loginUser);
  } catch (error: any) {
    // 检查是否是验证码相关错误（验证码过期或无效）
    const errorMsg = error?.response?.data?.message || error?.message || '';
    const shouldShowLocalError = error?.config?.suppressErrorMessage === true;
    const isCaptchaError =
      errorMsg.includes('验证码') || errorMsg.includes('CAPTCHA');
    if (isCaptchaError) {
      // 验证码过期或无效，需要重新获取验证码
      loginUser.captchaCode = '';
      if (shouldShowLocalError) {
        ElMessage.error('验证码已过期，请重新验证');
      }
      await reloadCaptcha();
      captchaDialogVisible.value = true;
    } else {
      // 密码等其他错误：清空验证码令牌，强制下次提交重新取码，
      // 使后端能按最新失败次数重新决定验证码类型(风险升级)。
      loginUser.captchaCode = '';
      if (shouldShowLocalError) {
        ElMessage.error(errorMsg || '用户名或密码错误');
      }
    }
  }
}
</script>

<template>
  <div>
    <AuthenticationLogin
      forget-password-path="/reset-password"
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :show-code-login="false"
      :show-qrcode-login="false"
      :show-register="true"
      :show-third-party-login="false"
      register-path="/register"
      @submit="handleSubmit"
    />
    <ElDialog
      v-model="captchaDialogVisible"
      align-center
      append-to-body
      destroy-on-close
      title="安全验证"
      :close-on-click-modal="false"
      :show-close="captchaStatus === 'ready' || captchaStatus === 'error'"
      width="min(420px, calc(100vw - 32px))"
    >
      <div class="captcha-dialog__body">
        <p class="captcha-dialog__tip">
          {{
            isPointCaptcha
              ? '请按提示依次点击文字完成本次登录确认'
              : '拖动拼图完成本次登录确认'
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

<style>
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
