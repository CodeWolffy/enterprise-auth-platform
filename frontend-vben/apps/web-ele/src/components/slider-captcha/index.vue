<script setup lang="ts">
import type { CaptchaTrackPayload } from './types';

import { computed, onUnmounted, ref, watch } from 'vue';

const props = defineProps<{
  backgroundHeight?: number;
  backgroundImage: string;
  backgroundWidth?: number;
  sliderHeight?: number;
  sliderImage: string;
  sliderWidth?: number;
  verifying?: boolean;
}>();

const emit = defineEmits<{
  (e: 'verify', track: CaptchaTrackPayload): void;
  (e: 'refresh'): void;
}>();

const loaded = ref(false);
const sliderLeft = ref(0);
const backgroundRef = ref<HTMLElement | null>(null);
const trackRef = ref<HTMLElement | null>(null);
const sliderRef = ref<HTMLElement | null>(null);

const TRACK_HANDLE_SIZE = 36;
const TRACK_HANDLE_INSET = 2;
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
  if (!loaded.value || props.verifying) {
    return;
  }

  const point = getPointerPoint(event);
  isDragging = true;
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
  <div class="slider-captcha" :class="{ loading: !loaded }">
    <div v-if="!loaded" class="captcha-loading">
      <span class="captcha-loading__spinner"></span>
      <span>加载中</span>
    </div>
    <div v-else class="captcha-container">
      <div ref="backgroundRef" class="captcha-image" :style="backgroundStyle">
        <img :src="backgroundImage" alt="" />
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
            height="14"
            stroke="currentColor"
            stroke-width="2"
            viewBox="0 0 24 24"
            width="14"
          >
            <path d="M1 4v6h6M23 20v-6h-6" />
            <path
              d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"
            />
          </svg>
        </button>
      </div>

      <div
        ref="trackRef"
        class="captcha-track"
        :class="{ 'is-verifying': verifying }"
        data-testid="captcha-track"
      >
        <div class="captcha-track__fill" :style="sliderTrackFillStyle"></div>
        <div
          class="captcha-track__handle"
          data-testid="captcha-handle"
          role="slider"
          :aria-valuemin="0"
          :aria-valuemax="100"
          :aria-valuenow="sliderProgress"
          :aria-label="verifying ? '校验中' : '拖动滑块完成验证'"
          :style="sliderButtonStyle"
          @mousedown="startDrag"
          @touchstart.prevent="startDrag"
        >
          <svg
            v-if="verifying"
            class="captcha-track__spinner"
            fill="none"
            height="14"
            viewBox="0 0 24 24"
            width="14"
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
          <svg
            v-else
            fill="none"
            height="14"
            stroke="currentColor"
            stroke-width="2"
            viewBox="0 0 24 24"
            width="14"
          >
            <path d="M5 12h14M12 5l7 7-7 7" />
          </svg>
        </div>
        <span class="captcha-track__label">
          {{ verifying ? '校验中...' : '拖动滑块完成验证' }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes spin {
  to {
    transform: rotate(360deg);
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
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.captcha-loading__spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--el-border-color);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.captcha-container {
  width: 100%;
}

.captcha-image {
  position: relative;
  width: 100%;
  aspect-ratio: 14 / 10;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.captcha-image > img {
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
  cursor: move;
  user-select: none;
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
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: rgb(255 255 255 / 90%);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-small);
  transition:
    color 0.2s ease,
    background 0.2s ease;
}

.captcha-refresh:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.captcha-track {
  position: relative;
  display: flex;
  align-items: center;
  height: 40px;
  margin-top: 12px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
}

.captcha-track__fill {
  position: absolute;
  inset: 0 auto 0 0;
  z-index: 0;
  background: var(--el-color-primary-light-9);
  border-radius: var(--el-border-radius-base) 0 0 var(--el-border-radius-base);
}

.captcha-track__label {
  position: absolute;
  right: 0;
  left: 0;
  z-index: 1;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
  pointer-events: none;
  user-select: none;
}

.captcha-track__handle {
  position: absolute;
  top: 2px;
  left: 2px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  color: var(--el-color-white);
  cursor: pointer;
  background: var(--el-color-primary);
  border-radius: calc(var(--el-border-radius-base) - 2px);
  transition: background 0.2s ease;
}

.captcha-track__handle:hover {
  background: var(--el-color-primary-light-3);
}

.captcha-track__handle:active {
  background: var(--el-color-primary-dark-2);
}

.captcha-track__spinner {
  animation: spin 0.7s linear infinite;
}

.captcha-track.is-verifying {
  pointer-events: none;
}

.captcha-track.is-verifying .captcha-track__label {
  color: var(--el-color-primary);
}

.captcha-track.is-verifying .captcha-track__handle {
  background: var(--el-color-primary-light-5);
}
</style>
