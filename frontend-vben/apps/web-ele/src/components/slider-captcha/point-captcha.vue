<script setup lang="ts">
import type { CaptchaStatus, CaptchaTrackPayload } from './types';

import { computed, ref, watch } from 'vue';

const props = defineProps<{
  backgroundHeight?: number;
  backgroundImage: string;
  backgroundWidth?: number;
  status?: CaptchaStatus;
  tipImage: string;
}>();

const emit = defineEmits<{
  (e: 'verify', payload: CaptchaTrackPayload): void;
  (e: 'refresh'): void;
}>();

interface ClickPoint {
  renderX: number;
  renderY: number;
  t: number;
}

const backgroundRef = ref<HTMLElement | null>(null);
const points = ref<ClickPoint[]>([]);
let startTime = 0;

const statusValue = computed<CaptchaStatus>(() => props.status ?? 'ready');
const isVerifying = computed(() => statusValue.value === 'verifying');
const isSuccess = computed(() => statusValue.value === 'success');
const isError = computed(() => statusValue.value === 'error');
const locked = computed(
  () => isVerifying.value || isSuccess.value || isError.value,
);

const bannerText = computed(() =>
  isSuccess.value ? '验证通过' : '验证失败，请重试',
);

const backgroundStyle = computed(() => {
  if (
    props.backgroundWidth &&
    props.backgroundHeight &&
    props.backgroundWidth > 0 &&
    props.backgroundHeight > 0
  ) {
    return {
      aspectRatio: `${props.backgroundWidth} / ${props.backgroundHeight}`,
    };
  }
  return {};
});

// 背景图切换（刷新/换一张）时清空已点选
watch(
  () => props.backgroundImage,
  () => resetPoints(),
);

// 校验失败后自动清空，便于重新点选
watch(statusValue, (value) => {
  if (value === 'error') {
    resetPoints();
  }
});

function resetPoints() {
  points.value = [];
  startTime = 0;
}

function handleClick(event: MouseEvent) {
  if (locked.value) {
    return;
  }
  const host = backgroundRef.value;
  if (!host) {
    return;
  }
  const rect = host.getBoundingClientRect();
  const x = event.clientX - rect.left;
  const y = event.clientY - rect.top;
  if (x < 0 || y < 0 || x > rect.width || y > rect.height) {
    return;
  }
  const now = Date.now();
  if (points.value.length === 0) {
    startTime = now;
  }
  points.value.push({ renderX: x, renderY: y, t: now - startTime });
}

function handleSubmit() {
  if (locked.value || points.value.length === 0) {
    return;
  }
  const host = backgroundRef.value;
  const renderW = host?.clientWidth || 0;
  const renderH = host?.clientHeight || 0;
  const rawW =
    props.backgroundWidth && props.backgroundWidth > 0
      ? props.backgroundWidth
      : renderW;
  const rawH =
    props.backgroundHeight && props.backgroundHeight > 0
      ? props.backgroundHeight
      : renderH;
  const scaleX = renderW > 0 ? rawW / renderW : 1;
  const scaleY = renderH > 0 ? rawH / renderH : 1;
  const stopTime = startTime + (points.value.at(-1)?.t ?? 0);

  emit('verify', {
    bgImageWidth: Math.round(rawW),
    bgImageHeight: Math.round(rawH),
    templateImageWidth: 0,
    templateImageHeight: 0,
    startTime,
    stopTime,
    left: 0,
    top: 0,
    // 后端按 type==='CLICK' 过滤并按点击顺序匹配，坐标需为原图像素
    trackList: points.value.map((point) => ({
      x: Number((point.renderX * scaleX).toFixed(2)),
      y: Number((point.renderY * scaleY).toFixed(2)),
      t: Number(point.t.toFixed(2)),
      type: 'CLICK',
    })),
  });
}
</script>

<template>
  <div class="point-captcha" :class="`is-${statusValue}`">
    <div
      ref="backgroundRef"
      class="point-captcha__image"
      :style="backgroundStyle"
      @click="handleClick"
    >
      <img class="point-captcha__bg" :src="backgroundImage" alt="" />

      <!-- 点选序号标记 -->
      <span
        v-for="(point, index) in points"
        :key="index"
        class="point-captcha__marker"
        :style="{ left: `${point.renderX}px`, top: `${point.renderY}px` }"
      >
        {{ index + 1 }}
      </span>

      <button
        class="point-captcha__refresh"
        type="button"
        title="刷新验证码"
        @click.stop="$emit('refresh')"
      >
        <svg
          fill="none"
          height="15"
          stroke="currentColor"
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          viewBox="0 0 24 24"
          width="15"
        >
          <path d="M1 4v6h6M23 20v-6h-6" />
          <path
            d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"
          />
        </svg>
      </button>

      <Transition name="point-captcha-banner">
        <div
          v-if="isSuccess || isError"
          class="point-captcha__banner"
          :class="isSuccess ? 'is-success' : 'is-error'"
        >
          {{ bannerText }}
        </div>
      </Transition>
    </div>

    <!-- 提示区：按序点击提示图中的文字 -->
    <div class="point-captcha__hint">
      <span class="point-captcha__hint-label">请依次点击</span>
      <img
        v-if="tipImage"
        class="point-captcha__hint-image"
        :src="tipImage"
        alt=""
      />
      <div class="point-captcha__actions">
        <button
          class="point-captcha__btn"
          type="button"
          :disabled="locked || points.length === 0"
          @click="resetPoints"
        >
          重置
        </button>
        <button
          class="point-captcha__btn point-captcha__btn--primary"
          type="button"
          :disabled="locked || points.length === 0"
          @click="handleSubmit"
        >
          {{ isVerifying ? '校验中…' : '确认' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.point-captcha {
  width: 100%;
}

.point-captcha__image {
  position: relative;
  width: 100%;
  aspect-ratio: 14 / 10;
  overflow: hidden;
  cursor: pointer;
  background: var(--el-fill-color-light);
  border-radius: 10px;
  box-shadow:
    0 6px 18px rgb(0 0 0 / 10%),
    0 0 0 1px var(--el-border-color-lighter) inset;
}

.point-captcha__bg {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: fill;
  user-select: none;
  pointer-events: none;
}

.point-captcha__marker {
  position: absolute;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-white);
  background: var(--el-color-primary);
  border: 2px solid var(--el-color-white);
  border-radius: 50%;
  box-shadow: 0 2px 6px rgb(0 0 0 / 30%);
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.point-captcha__refresh {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: rgb(255 255 255 / 82%);
  border: none;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgb(0 0 0 / 16%);
  backdrop-filter: blur(4px);
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.point-captcha__refresh:hover {
  color: var(--el-color-primary);
  background: rgb(255 255 255 / 96%);
}

.point-captcha__banner {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-white);
  letter-spacing: 0.5px;
}

.point-captcha__banner.is-success {
  background: color-mix(in srgb, var(--el-color-success) 90%, transparent);
}

.point-captcha__banner.is-error {
  background: color-mix(in srgb, var(--el-color-danger) 90%, transparent);
}

.point-captcha-banner-enter-active,
.point-captcha-banner-leave-active {
  transition:
    transform 0.3s ease,
    opacity 0.3s ease;
}

.point-captcha-banner-enter-from,
.point-captcha-banner-leave-to {
  opacity: 0;
  transform: translateY(100%);
}

.point-captcha__hint {
  display: flex;
  gap: 10px;
  align-items: center;
  height: 46px;
  padding: 0 10px;
  margin-top: 16px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.point-captcha__hint-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.point-captcha__hint-image {
  height: 28px;
  border-radius: 4px;
}

.point-captcha__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.point-captcha__btn {
  height: 30px;
  padding: 0 14px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  transition:
    color 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease;
}

.point-captcha__btn:hover:not(:disabled) {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.point-captcha__btn--primary {
  color: var(--el-color-white);
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.point-captcha__btn--primary:hover:not(:disabled) {
  color: var(--el-color-white);
  background: var(--el-color-primary-light-3);
  border-color: var(--el-color-primary-light-3);
}

.point-captcha__btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
