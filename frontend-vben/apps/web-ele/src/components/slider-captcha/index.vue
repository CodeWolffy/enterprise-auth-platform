<script setup lang="ts">
import type { CaptchaStatus, CaptchaTrackPayload } from './types';

import { computed, onUnmounted, ref, watch } from 'vue';

const props = defineProps<{
  backgroundHeight?: number;
  backgroundImage: string;
  backgroundWidth?: number;
  sliderHeight?: number;
  sliderImage: string;
  sliderWidth?: number;
  status?: CaptchaStatus;
}>();

const emit = defineEmits<{
  (e: 'verify', track: CaptchaTrackPayload): void;
  (e: 'refresh'): void;
}>();

const loaded = ref(false);
const sliderLeft = ref(0);
const dragging = ref(false);
const backgroundRef = ref<HTMLElement | null>(null);
const trackRef = ref<HTMLElement | null>(null);
const sliderRef = ref<HTMLElement | null>(null);

const TRACK_HANDLE_SIZE = 40;
const TRACK_HANDLE_INSET = 3;
const TRACK_HANDLE_TRAVEL_OFFSET = TRACK_HANDLE_SIZE + TRACK_HANDLE_INSET * 2;
const MIN_TRACK_POINTS = 18;
const MAX_TRACK_POINTS = 80;

let isDragging = false;
let startX = 0;
let startY = 0;
let lastTrackY = 0;
let track: Array<{ t: number; x: number; y: number }> = [];
let startTime = 0;
let verifyEmitted = false;

const statusValue = computed<CaptchaStatus>(() => props.status ?? 'ready');
const isVerifying = computed(() => statusValue.value === 'verifying');
const isSuccess = computed(() => statusValue.value === 'success');
const isError = computed(() => statusValue.value === 'error');
// 校验中 / 已出结果时锁定拖动
const locked = computed(
  () => isVerifying.value || isSuccess.value || isError.value,
);

const hintText = computed(() =>
  isVerifying.value ? '正在校验…' : '向右拖动滑块完成拼图',
);

// 结果横幅文案（覆盖在拼图上，成功绿 / 失败红）
const bannerText = computed(() =>
  isSuccess.value ? '验证通过' : '验证失败，请重试',
);

const sliderDisplayWidth = computed(() => {
  const renderedBgWidth = backgroundRef.value?.clientWidth || 0;
  if (
    renderedBgWidth > 0 &&
    props.backgroundWidth &&
    props.backgroundWidth > 0 &&
    props.sliderWidth &&
    props.sliderWidth > 0
  ) {
    return Math.max(
      Math.round((props.sliderWidth / props.backgroundWidth) * renderedBgWidth),
      24,
    );
  }
  return 50;
});

const sliderOverlayStyle = computed(() => ({
  left: `${sliderLeft.value}px`,
  width: `${sliderDisplayWidth.value}px`,
}));

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

const dragMetrics = computed(() => {
  const bgWidth = backgroundRef.value?.clientWidth || 300;
  const trackWidth = trackRef.value?.clientWidth || bgWidth;
  return {
    maxImageLeft: Math.max(bgWidth - sliderDisplayWidth.value, 0),
    maxTrackLeft: Math.max(trackWidth - TRACK_HANDLE_TRAVEL_OFFSET, 0),
  };
});

const sliderButtonLeft = computed(() => {
  const { maxImageLeft, maxTrackLeft } = dragMetrics.value;
  if (maxTrackLeft <= 0 || maxImageLeft <= 0) {
    return 0;
  }
  return Math.max(
    0,
    Math.min((sliderLeft.value / maxImageLeft) * maxTrackLeft, maxTrackLeft),
  );
});

const sliderButtonStyle = computed(() => ({
  transform: `translateX(${sliderButtonLeft.value}px)`,
}));

const sliderTrackFillStyle = computed(() => ({
  width: `${sliderButtonLeft.value + TRACK_HANDLE_SIZE + TRACK_HANDLE_INSET}px`,
}));

const sliderProgress = computed(() => {
  const { maxTrackLeft } = dragMetrics.value;
  if (maxTrackLeft <= 0) return 0;
  return Math.min(
    100,
    Math.round((sliderButtonLeft.value / maxTrackLeft) * 100),
  );
});

function resetSlider() {
  sliderLeft.value = 0;
  isDragging = false;
  dragging.value = false;
  startX = 0;
  startY = 0;
  lastTrackY = 0;
  track = [];
  startTime = 0;
  verifyEmitted = false;
}

watch(
  () => [props.backgroundImage, props.sliderImage],
  () => {
    loaded.value = false;
    resetSlider();
    window.setTimeout(() => {
      loaded.value = true;
    }, 100);
  },
  { immediate: true },
);

function startDrag(event: MouseEvent | TouchEvent) {
  if (!loaded.value || locked.value) {
    return;
  }

  const point = getPointerPoint(event);
  isDragging = true;
  dragging.value = true;
  startX = point.x;
  startY = point.y;
  lastTrackY = 0;
  track = [{ x: 0, y: 0, t: 0 }];
  startTime = Date.now();
  verifyEmitted = false;

  document.addEventListener('mousemove', onDrag);
  document.addEventListener('mouseup', endDrag);
  document.addEventListener('touchmove', onDrag, { passive: false });
  document.addEventListener('touchend', endDrag);
}

function onDrag(event: MouseEvent | TouchEvent) {
  if (!isDragging) {
    return;
  }

  if (event.cancelable && 'touches' in event) {
    event.preventDefault();
  }

  const point = getPointerPoint(event);
  const deltaX = point.x - startX;
  const deltaY = point.y - startY;
  const { maxImageLeft, maxTrackLeft } = dragMetrics.value;
  const trackLeft = Math.max(0, Math.min(deltaX, maxTrackLeft));

  sliderLeft.value =
    maxTrackLeft > 0 ? (trackLeft / maxTrackLeft) * maxImageLeft : 0;
  lastTrackY = deltaY;
  track.push({
    x: sliderLeft.value,
    y: lastTrackY,
    t: Date.now() - startTime,
  });
}

function endDrag() {
  if (!isDragging) {
    return;
  }

  isDragging = false;
  dragging.value = false;
  const stopTime = Date.now();

  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mouseup', endDrag);
  document.removeEventListener('touchmove', onDrag);
  document.removeEventListener('touchend', endDrag);

  if (track.length <= 1 || verifyEmitted) {
    return;
  }
  verifyEmitted = true;

  const normalizedTrack = normalizeTrack([
    ...track,
    {
      x: sliderLeft.value,
      y: lastTrackY,
      t: stopTime - startTime,
    },
  ]);

  const renderBgWidth = backgroundRef.value?.clientWidth || 0;
  const renderBgHeight = backgroundRef.value?.clientHeight || 0;
  const renderSliderWidth = sliderDisplayWidth.value;
  const renderSliderHeight = sliderRef.value?.clientHeight || 0;

  const rawBgWidth =
    props.backgroundWidth && props.backgroundWidth > 0
      ? props.backgroundWidth
      : renderBgWidth;
  const rawBgHeight =
    props.backgroundHeight && props.backgroundHeight > 0
      ? props.backgroundHeight
      : renderBgHeight;
  const rawSliderWidth =
    props.sliderWidth && props.sliderWidth > 0
      ? props.sliderWidth
      : renderSliderWidth;
  const rawSliderHeight =
    props.sliderHeight && props.sliderHeight > 0
      ? props.sliderHeight
      : renderSliderHeight;

  const scaleX = renderBgWidth > 0 ? rawBgWidth / renderBgWidth : 1;
  const scaleY = renderBgHeight > 0 ? rawBgHeight / renderBgHeight : 1;

  emit('verify', {
    bgImageWidth: Math.round(rawBgWidth),
    bgImageHeight: Math.round(rawBgHeight),
    templateImageWidth: Math.round(rawSliderWidth),
    templateImageHeight: Math.round(rawSliderHeight),
    startTime,
    stopTime,
    left: Math.round(sliderLeft.value * scaleX),
    top: 0,
    trackList: normalizedTrack.map((point) => ({
      x: Number((point.x * scaleX).toFixed(2)),
      y: Number((point.y * scaleY).toFixed(2)),
      t: Number(point.t.toFixed(2)),
      type: 'MOVE',
    })),
  });
}

function getPointerPoint(event: MouseEvent | TouchEvent) {
  if ('touches' in event) {
    const touch = event.touches[0] ?? event.changedTouches[0];
    return {
      x: touch?.clientX ?? startX,
      y: touch?.clientY ?? startY,
    };
  }

  return {
    x: event.clientX,
    y: event.clientY,
  };
}

function normalizeTrack(points: Array<{ t: number; x: number; y: number }>) {
  const deduped = points.filter((point, index) => {
    if (index === 0) {
      return true;
    }
    const previous = points[index - 1]!;
    return (
      point.x !== previous.x || point.y !== previous.y || point.t !== previous.t
    );
  });

  if (deduped.length < 2) {
    return deduped;
  }

  const interpolated: Array<{ t: number; x: number; y: number }> = [
    deduped[0]!,
  ];
  for (let index = 1; index < deduped.length; index += 1) {
    const from = interpolated[interpolated.length - 1]!;
    const to = deduped[index]!;
    const steps = Math.max(
      1,
      Math.ceil(
        Math.max(Math.abs(to.x - from.x), Math.abs(to.y - from.y)) / 40,
      ),
    );

    for (let step = 1; step <= steps; step += 1) {
      const progress = step / steps;
      interpolated.push({
        x: from.x + (to.x - from.x) * progress,
        y: from.y + (to.y - from.y) * progress,
        t: from.t + (to.t - from.t) * progress,
      });
    }
  }

  const targetLength = Math.min(
    Math.max(interpolated.length, MIN_TRACK_POINTS),
    MAX_TRACK_POINTS,
  );

  const first = interpolated[0]!;
  const last = interpolated[interpolated.length - 1]!;
  return Array.from({ length: targetLength }, (_, index) => {
    const progress = index / (targetLength - 1);
    return {
      x: first.x + (last.x - first.x) * progress,
      y: first.y + (last.y - first.y) * progress,
      t: first.t + (last.t - first.t) * progress,
    };
  });
}

onUnmounted(() => {
  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mouseup', endDrag);
  document.removeEventListener('touchmove', onDrag);
  document.removeEventListener('touchend', endDrag);
});
</script>

<template>
  <div
    class="slider-captcha"
    :class="[`is-${statusValue}`, { loading: !loaded, dragging }]"
  >
    <div v-if="!loaded" class="captcha-loading">
      <span class="captcha-loading__spinner"></span>
      <span>加载中</span>
    </div>
    <div v-else class="captcha-container">
      <div ref="backgroundRef" class="captcha-image" :style="backgroundStyle">
        <img class="captcha-image__bg" :src="backgroundImage" alt="" />
        <div ref="sliderRef" class="captcha-slider" :style="sliderOverlayStyle">
          <img :src="sliderImage" alt="" />
        </div>
        <button
          class="captcha-refresh"
          type="button"
          title="刷新验证码"
          @click="$emit('refresh')"
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

        <!-- 结果横幅：成功/失败时覆盖在拼图底部 -->
        <Transition name="captcha-banner">
          <div
            v-if="isSuccess || isError"
            class="captcha-banner"
            :class="isSuccess ? 'is-success' : 'is-error'"
          >
            <svg
              v-if="isSuccess"
              fill="none"
              height="14"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2.5"
              viewBox="0 0 24 24"
              width="14"
            >
              <path d="M4 12.5 9 17.5 20 6.5" />
            </svg>
            <svg
              v-else
              fill="none"
              height="14"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2.5"
              viewBox="0 0 24 24"
              width="14"
            >
              <path d="M6 6l12 12M18 6 6 18" />
            </svg>
            <span>{{ bannerText }}</span>
          </div>
        </Transition>
      </div>

      <div
        ref="trackRef"
        class="captcha-track"
        :class="{
          'is-verifying': isVerifying,
          'is-success': isSuccess,
          'is-error': isError,
          'is-ready': statusValue === 'ready',
        }"
        data-testid="captcha-track"
      >
        <div class="captcha-track__fill" :style="sliderTrackFillStyle"></div>
        <span v-if="!isSuccess && !isError" class="captcha-track__label">{{
          hintText
        }}</span>
        <div
          class="captcha-track__handle"
          data-testid="captcha-handle"
          role="slider"
          :aria-valuemin="0"
          :aria-valuemax="100"
          :aria-valuenow="sliderProgress"
          :aria-label="hintText"
          :style="sliderButtonStyle"
          @mousedown="startDrag"
          @touchstart.prevent="startDrag"
        >
          <!-- 校验中 -->
          <svg
            v-if="isVerifying"
            class="captcha-track__spinner"
            fill="none"
            height="16"
            viewBox="0 0 24 24"
            width="16"
          >
            <circle
              cx="12"
              cy="12"
              r="9"
              stroke="currentColor"
              stroke-dasharray="28 28"
              stroke-linecap="round"
              stroke-width="2.5"
            />
          </svg>
          <!-- 成功 -->
          <svg
            v-else-if="isSuccess"
            fill="none"
            height="16"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2.5"
            viewBox="0 0 24 24"
            width="16"
          >
            <path d="M4 12.5 9 17.5 20 6.5" />
          </svg>
          <!-- 失败 -->
          <svg
            v-else-if="isError"
            fill="none"
            height="16"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2.5"
            viewBox="0 0 24 24"
            width="16"
          >
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
          <!-- 待拖动：双箭头 -->
          <svg
            v-else
            class="captcha-track__chevron"
            fill="none"
            height="16"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            viewBox="0 0 24 24"
            width="16"
          >
            <path d="M6 6l5 6-5 6M13 6l5 6-5 6" />
          </svg>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes captcha-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 提示文字微光扫过（主流滑块验证码的呼吸提示效果） */
@keyframes captcha-shimmer {
  0% {
    background-position: 160% 0;
  }

  100% {
    background-position: -60% 0;
  }
}

@keyframes captcha-chevron {
  0%,
  100% {
    transform: translateX(-1px);
    opacity: 0.75;
  }

  50% {
    transform: translateX(2px);
    opacity: 1;
  }
}

@keyframes captcha-shake {
  10%,
  90% {
    transform: translateX(-1px);
  }

  20%,
  80% {
    transform: translateX(2px);
  }

  30%,
  50%,
  70% {
    transform: translateX(-4px);
  }

  40%,
  60% {
    transform: translateX(4px);
  }
}

.slider-captcha {
  position: relative;
  width: 100%;
  isolation: isolate;
}

.captcha-loading {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 200px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.captcha-loading__spinner {
  width: 22px;
  height: 22px;
  border: 2px solid var(--el-border-color);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: captcha-spin 0.7s linear infinite;
}

.captcha-container {
  width: 100%;
}

/* ---------- 拼图图片 ---------- */
.captcha-image {
  position: relative;
  width: 100%;
  aspect-ratio: 14 / 10;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border-radius: 10px;
  box-shadow:
    0 6px 18px rgb(0 0 0 / 10%),
    0 0 0 1px var(--el-border-color-lighter) inset;
}

.captcha-image__bg {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: fill;
}

.captcha-slider {
  position: absolute;
  top: 0;
  z-index: 1;
  height: 100%;
  cursor: grab;
  user-select: none;
  filter: drop-shadow(0 0 4px rgb(0 0 0 / 35%));
  transition: filter 0.2s ease;
}

.dragging .captcha-slider {
  cursor: grabbing;
  filter: drop-shadow(0 0 7px rgb(0 0 0 / 45%));
}

.captcha-slider img {
  width: 100%;
  height: 100%;
  pointer-events: none;
  object-fit: fill;
}

.captcha-refresh {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
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
    background 0.2s ease,
    transform 0.35s ease;
}

.captcha-refresh:hover {
  color: var(--el-color-primary);
  background: rgb(255 255 255 / 96%);
  transform: rotate(-90deg);
}

.captcha-refresh:active {
  transform: rotate(-180deg);
}

/* ---------- 滑轨 ---------- */
.captcha-track {
  position: relative;
  display: flex;
  align-items: center;
  height: 46px;
  margin-top: 16px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  box-shadow: inset 0 1px 3px rgb(0 0 0 / 5%);
  transition: border-color 0.2s ease;
}

.captcha-track__fill {
  position: absolute;
  inset: 0 auto 0 0;
  z-index: 0;
  background: linear-gradient(
    90deg,
    var(--el-color-primary-light-3),
    var(--el-color-primary)
  );
  opacity: 0.9;
  transition: background 0.25s ease;
}

.captcha-track__label {
  position: absolute;
  right: 0;
  left: 0;
  z-index: 1;
  padding-left: 20px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  text-align: center;
  letter-spacing: 0.5px;
  pointer-events: none;
  user-select: none;
}

/* 待拖动状态：文字微光扫过 */
.captcha-track.is-ready .captcha-track__label {
  color: transparent;
  background: linear-gradient(
    100deg,
    var(--el-text-color-secondary) 0%,
    var(--el-text-color-secondary) 38%,
    var(--el-text-color-primary) 50%,
    var(--el-text-color-secondary) 62%,
    var(--el-text-color-secondary) 100%
  );
  background-size: 220% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  animation: captcha-shimmer 2.8s linear infinite;
}

.captcha-track__handle {
  position: absolute;
  top: 3px;
  left: 3px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  color: var(--el-color-primary);
  cursor: grab;
  background: var(--el-bg-color, #fff);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 18%);
  transition:
    color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease;
}

.captcha-track__handle:hover {
  box-shadow: 0 3px 12px rgb(0 0 0 / 26%);
}

.dragging .captcha-track__handle {
  cursor: grabbing;
  box-shadow: 0 3px 14px rgb(0 0 0 / 30%);
}

.captcha-track__chevron {
  animation: captcha-chevron 1.4s ease-in-out infinite;
}

.captcha-track__spinner {
  color: var(--el-color-primary);
  animation: captcha-spin 0.7s linear infinite;
}

/* ---------- 校验中 ---------- */
.captcha-track.is-verifying {
  pointer-events: none;
}

.captcha-track.is-verifying .captcha-track__label {
  padding-left: 20px;
  color: var(--el-color-primary);
}

/* ---------- 成功 ---------- */
.captcha-track.is-success {
  pointer-events: none;
  border-color: var(--el-color-success);
}

.captcha-track.is-success .captcha-track__fill {
  background: linear-gradient(
    90deg,
    var(--el-color-success-light-3),
    var(--el-color-success)
  );
  opacity: 1;
}

.captcha-track.is-success .captcha-track__handle {
  color: var(--el-color-white);
  background: var(--el-color-success);
}

/* ---------- 失败 ---------- */
.captcha-track.is-error {
  pointer-events: none;
  border-color: var(--el-color-danger);
  animation: captcha-shake 0.5s ease;
}

.captcha-track.is-error .captcha-track__fill {
  background: linear-gradient(
    90deg,
    var(--el-color-danger-light-3),
    var(--el-color-danger)
  );
  opacity: 1;
}

.captcha-track.is-error .captcha-track__handle {
  color: var(--el-color-white);
  background: var(--el-color-danger);
}

/* ---------- 结果横幅 ---------- */
.captcha-banner {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 3;
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: center;
  height: 34px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-white);
  letter-spacing: 0.5px;
}

.captcha-banner.is-success {
  background: color-mix(in srgb, var(--el-color-success) 90%, transparent);
}

.captcha-banner.is-error {
  background: color-mix(in srgb, var(--el-color-danger) 90%, transparent);
}

.captcha-banner-enter-active,
.captcha-banner-leave-active {
  transition:
    transform 0.3s ease,
    opacity 0.3s ease;
}

.captcha-banner-enter-from,
.captcha-banner-leave-to {
  opacity: 0;
  transform: translateY(100%);
}
</style>
