<template>
  <div class="slider-captcha" :class="{ loading: !loaded }">
    <div v-if="!loaded" class="captcha-loading">
      <div class="captcha-loading__spinner"></div>
      <span>加载中</span>
    </div>
    <div v-else class="captcha-container">
      <div ref="backgroundRef" class="captcha-image" :style="backgroundStyle">
        <img :src="backgroundImage" alt="" />
        <div ref="sliderRef" class="captcha-slider" :style="sliderOverlayStyle">
          <img :src="sliderImage" alt="" />
        </div>
        <button class="captcha-refresh" type="button" title="刷新验证码" @click="$emit('refresh')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
            <path d="M1 4v6h6M23 20v-6h-6" />
            <path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15" />
          </svg>
        </button>
      </div>

<div ref="trackRef" class="captcha-track" :class="{ 'is-verifying': verifying }">
    <div class="captcha-track__fill" :style="sliderTrackFillStyle"></div>
    <div
      class="captcha-track__handle"
      :style="sliderButtonStyle"
      @mousedown="startDrag"
      @touchstart.prevent="startDrag"
    >
      <svg v-if="verifying" class="captcha-track__spinner" viewBox="0 0 24 24" fill="none" width="14" height="14">
        <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2.5" stroke-dasharray="28 28" stroke-linecap="round" />
      </svg>
      <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
      <path d="M5 12h14M12 5l7 7-7 7" />
      </svg>
    </div>
    <span class="captcha-track__label">{{ verifying ? '校验中...' : '拖动滑块完成验证' }}</span>
  </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import type { CaptchaTrackPayload } from '@/types/auth'

const props = defineProps<{
  backgroundImage: string
  sliderImage: string
  backgroundWidth?: number
  backgroundHeight?: number
  sliderWidth?: number
  sliderHeight?: number
  verifying?: boolean
}>()

const emit = defineEmits<{
  (e: 'verify', track: CaptchaTrackPayload): void
  (e: 'refresh'): void
}>()

const loaded = ref(false)
const sliderLeft = ref(0)
const backgroundRef = ref<HTMLElement | null>(null)
const trackRef = ref<HTMLElement | null>(null)
const sliderRef = ref<HTMLElement | null>(null)

let isDragging = false
let startX = 0
let track: Array<{ x: number; y: number; t: number }> = []
let startTime = 0

const sliderDisplayWidth = computed(() => {
  const renderedBgWidth = backgroundRef.value?.clientWidth || 0
  if (renderedBgWidth > 0 && props.backgroundWidth && props.backgroundWidth > 0 && props.sliderWidth && props.sliderWidth > 0) {
    return Math.max(Math.round((props.sliderWidth / props.backgroundWidth) * renderedBgWidth), 24)
  }
  return 50
})

const sliderOverlayStyle = computed(() => ({
  left: `${sliderLeft.value}px`,
  width: `${sliderDisplayWidth.value}px`,
}))

const backgroundStyle = computed(() => {
  if (props.backgroundWidth && props.backgroundHeight && props.backgroundWidth > 0 && props.backgroundHeight > 0) {
    return { aspectRatio: `${props.backgroundWidth} / ${props.backgroundHeight}` }
  }
  return {}
})

const sliderButtonLeft = computed(() => {
  const trackWidth = trackRef.value?.clientWidth || 0
  const bgWidth = backgroundRef.value?.clientWidth || 0
  if (trackWidth <= 0 || bgWidth <= 0) {
    return 0
  }
  const maxImageLeft = Math.max(bgWidth - sliderDisplayWidth.value, 1)
  const maxTrackLeft = Math.max(trackWidth - 32, 0)
  return Math.max(0, Math.min((sliderLeft.value / maxImageLeft) * maxTrackLeft, maxTrackLeft))
})

const sliderButtonStyle = computed(() => ({
  transform: `translateX(${sliderButtonLeft.value}px)`,
}))

const sliderTrackFillStyle = computed(() => ({
  width: `${sliderButtonLeft.value + 32}px`,
}))

watch(
  () => [props.backgroundImage, props.sliderImage],
  () => {
    loaded.value = false
    window.setTimeout(() => {
      loaded.value = true
      sliderLeft.value = 0
    }, 100)
  },
  { immediate: true },
)

function startDrag(event: MouseEvent | TouchEvent) {
  if (!loaded.value || props.verifying) {
    return
  }

  isDragging = true
  startX = 'touches' in event ? event.touches[0].clientX : event.clientX
  track = [{ x: 0, y: 0, t: 0 }]
  startTime = Date.now()

  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', endDrag)
  document.addEventListener('touchmove', onDrag)
  document.addEventListener('touchend', endDrag)
}

function onDrag(event: MouseEvent | TouchEvent) {
  if (!isDragging) {
    return
  }

  const currentX = 'touches' in event ? event.touches[0].clientX : event.clientX
  const deltaX = currentX - startX
  const containerWidth = backgroundRef.value?.clientWidth || 300
  const currentSliderWidth = sliderDisplayWidth.value
  const maxLeft = Math.max(containerWidth - currentSliderWidth, 0)

  sliderLeft.value = Math.max(0, Math.min(deltaX, maxLeft))
  track.push({
    x: sliderLeft.value,
    y: 0,
    t: Date.now() - startTime,
  })
}

function endDrag() {
  if (!isDragging) {
    return
  }

  isDragging = false
  const stopTime = Date.now()

  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', endDrag)

  if (track.length <= 1) {
    return
  }

  const normalizedTrack = [
    ...track,
    {
      x: sliderLeft.value,
      y: 0,
      t: stopTime - startTime,
    },
  ]

  const renderBgWidth = backgroundRef.value?.clientWidth || 0
  const renderBgHeight = backgroundRef.value?.clientHeight || 0
  const renderSliderWidth = sliderDisplayWidth.value
  const renderSliderHeight = sliderRef.value?.clientHeight || 0

  const rawBgWidth = props.backgroundWidth && props.backgroundWidth > 0 ? props.backgroundWidth : renderBgWidth
  const rawBgHeight = props.backgroundHeight && props.backgroundHeight > 0 ? props.backgroundHeight : renderBgHeight
  const rawSliderWidth = props.sliderWidth && props.sliderWidth > 0 ? props.sliderWidth : renderSliderWidth
  const rawSliderHeight = props.sliderHeight && props.sliderHeight > 0 ? props.sliderHeight : renderSliderHeight

  const scaleX = renderBgWidth > 0 ? rawBgWidth / renderBgWidth : 1
  const scaleY = renderBgHeight > 0 ? rawBgHeight / renderBgHeight : 1

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
  })
}

onUnmounted(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', endDrag)
})
</script>

<style scoped>
.slider-captcha {
  width: 100%;
  position: relative;
}

.captcha-loading {
  width: 100%;
  min-height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-radius: 10px;
  background: #f8fafc;
  color: #94a3b8;
  font-size: 13px;
}

.captcha-loading__spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #e2e8f0;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.captcha-container {
  position: relative;
  width: 100%;
}

.captcha-image {
  position: relative;
  width: 100%;
  aspect-ratio: 14 / 10;
  overflow: hidden;
  border-radius: 10px;
  background: #f1f5f9;
}

.captcha-image > img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: fill;
}

.captcha-slider {
  position: absolute;
  top: 0;
  height: 100%;
  user-select: none;
  cursor: grab;
}

.captcha-slider:active {
  cursor: grabbing;
}

.captcha-slider img {
  width: 100%;
  height: 100%;
  object-fit: fill;
  pointer-events: none;
}

.captcha-refresh {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 26px;
  height: 26px;
  padding: 0;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  cursor: pointer;
  transition: background 0.15s;
  backdrop-filter: blur(4px);
}

.captcha-refresh:hover {
  background: rgba(0, 0, 0, 0.55);
}

.captcha-track {
  position: relative;
  margin-top: 10px;
  height: 36px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  overflow: hidden;
  display: flex;
  align-items: center;
}

.captcha-track__fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: rgba(37, 99, 235, 0.06);
  border-radius: 10px 0 0 10px;
  transition: none;
}

.captcha-track__label {
  position: absolute;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
  pointer-events: none;
  user-select: none;
}

.captcha-track__handle {
  position: absolute;
  left: 3px;
  top: 3px;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #2563eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
  z-index: 1;
}

.captcha-track__handle:hover {
  border-color: #2563eb;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.15);
}

.captcha-track__handle:active {
  box-shadow: 0 1px 2px rgba(37, 99, 235, 0.2);
}

.captcha-track__spinner {
  animation: spin 0.7s linear infinite;
}

.captcha-track.is-verifying {
  pointer-events: none;
  opacity: 0.7;
}
</style>
