<template>
  <div class="characters-wrapper">
    <div ref="sceneRef" class="characters-scene">
      <div class="character char-purple" :class="{ 'success-jump': isSuccess }" :style="purpleStyle">
        <div class="eyes" :style="purpleEyesStyle">
          <div class="eyeball" :class="{ blink: purpleBlinking }">
            <div class="pupil" :style="purplePupilStyle"></div>
          </div>
          <div class="eyeball" :class="{ blink: purpleBlinking }">
            <div class="pupil" :style="purplePupilStyle"></div>
          </div>
        </div>
        <div
          class="purple-mouth"
          :class="{
            'error-face': isError,
            'success-face': isSuccess,
          }"
          :style="purpleMouthStyle"
        ></div>
      </div>

      <div class="character char-black" :class="{ 'success-jump': isSuccess }" :style="blackStyle">
        <div class="eyes" :style="blackEyesStyle">
          <div class="eyeball" :class="{ blink: blackBlinking }">
            <div class="pupil" :style="blackPupilStyle"></div>
          </div>
          <div class="eyeball" :class="{ blink: blackBlinking }">
            <div class="pupil" :style="blackPupilStyle"></div>
          </div>
        </div>
      </div>

      <div class="character char-orange" :class="{ 'success-jump': isSuccess }" :style="orangeStyle">
        <div class="eyes bare-eyes" :style="orangeEyesStyle">
          <div class="bare-pupil" :style="orangePupilStyle"></div>
          <div class="bare-pupil" :style="orangePupilStyle"></div>
        </div>
        <div
          class="orange-mouth"
          :class="{
            'error-face': isError || registerMatchState === 'mismatch',
            'success-face': isSuccess || registerMatchState === 'match',
          }"
          :style="orangeMouthStyle"
        ></div>
      </div>

      <div class="character char-yellow" :class="{ 'success-jump': isSuccess }" :style="yellowStyle">
        <div class="eyes bare-eyes" :style="yellowEyesStyle">
          <div class="bare-pupil" :style="yellowPupilStyle"></div>
          <div class="bare-pupil" :style="yellowPupilStyle"></div>
        </div>
        <div
          class="yellow-mouth"
          :class="{
            'error-face': isError || registerMatchState === 'mismatch',
            'success-face': isSuccess || registerMatchState === 'match',
          }"
          :style="yellowMouthStyle"
        ></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

type SceneMode = 'login' | 'register'
type FocusState = 'none' | 'text' | 'password'
type SceneStatus = 'idle' | 'error' | 'success'

interface DriftState {
  pSkew: number
  pX: number
  pEyeX: number
  pEyeY: number
  bSkew: number
  bX: number
  bEyeX: number
  bEyeY: number
}

interface Props {
  mode?: SceneMode
  focusedField?: FocusState
  passwordVisible?: boolean
  status?: SceneStatus
  confirmMatch?: boolean | null
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'login',
  focusedField: 'none',
  passwordVisible: false,
  status: 'idle',
  confirmMatch: null,
})

const sceneRef = ref<HTMLElement | null>(null)
const lookX = ref(0)
const lookY = ref(0)
const inputLookX = ref(0)
const inputLookY = ref(0)
const purpleBlinking = ref(false)
const blackBlinking = ref(false)
const purplePeekWhenAway = ref(false)
const blackPeekWhenAway = ref(false)
const textMutualGaze = ref(false)

const drift = ref<DriftState>({
  pSkew: 0,
  pX: 0,
  pEyeX: 0,
  pEyeY: 0,
  bSkew: 0,
  bX: 0,
  bEyeX: 0,
  bEyeY: 0,
})

let driftTimer: number | null = null
let purplePeekTimer: number | null = null
let blackPeekTimer: number | null = null
let textMutualGazeTimer: number | null = null
let inputTrackingRafId: number | null = null
let pointerRafId: number | null = null
let pointerPendingX = 0
let pointerPendingY = 0
let inputTrackingActive = false
let entranceTimer: number | null = null
const entranceReady = ref(false)
const blinkTimers: number[] = []

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function clearTimer(timer: number | null) {
  if (timer !== null) {
    window.clearTimeout(timer)
    window.clearInterval(timer)
  }
}

function clearAnimationFrame(frameId: number | null) {
  if (frameId !== null) {
    window.cancelAnimationFrame(frameId)
  }
}

function random(min: number, max: number) {
  return Math.random() * (max - min) + min
}

function updateAxis(target: { value: number }, next: number, epsilon = 0.08) {
  const delta = next - target.value
  if (Math.abs(delta) <= epsilon) {
    target.value = next
    return
  }
  target.value += delta * 0.16
}

const isTyping = computed(() => props.focusedField === 'text')
const isPasswordFocus = computed(() => props.focusedField === 'password')
const isLookingAway = computed(() => isPasswordFocus.value && !props.passwordVisible)
const isShowingPassword = computed(() => props.passwordVisible)
const isError = computed(() => props.status === 'error')
const isSuccess = computed(() => props.status === 'success')
const registerMatchState = computed<'match' | 'mismatch' | null>(() => {
  if (props.mode !== 'register') {
    return null
  }
  if (props.confirmMatch === true) {
    return 'match'
  }
  if (props.confirmMatch === false) {
    return 'mismatch'
  }
  return null
})

const bodySkew = computed(() => clamp(-(lookX.value / 2.5), -6, 6))
const pupilX = computed(() => clamp(lookX.value * 0.35, -5, 5))
const pupilY = computed(() => clamp(lookY.value * 0.35, -5, 5))
const activeLookX = computed(() => (isTyping.value && !textMutualGaze.value ? inputLookX.value : lookX.value))
const activeLookY = computed(() => (isTyping.value && !textMutualGaze.value ? inputLookY.value : lookY.value))
const activeBodySkew = computed(() => clamp(-(activeLookX.value / 2.5), -6, 6))
const activePupilX = computed(() => clamp(activeLookX.value * 0.35, -5, 5))
const activePupilY = computed(() => clamp(activeLookY.value * 0.35, -5, 5))

function resetDrift() {
  drift.value = {
    pSkew: 0,
    pX: 0,
    pEyeX: 0,
    pEyeY: 0,
    bSkew: 0,
    bX: 0,
    bEyeX: 0,
    bEyeY: 0,
  }
}

function refreshDrift() {
  drift.value = {
    pSkew: random(-1.2, 1.2),
    pX: random(-2, 2),
    pEyeX: random(-1.2, 1.2),
    pEyeY: random(-1.2, 1.2),
    bSkew: random(-1, 1),
    bX: random(-1.5, 1.5),
    bEyeX: random(-1, 1),
    bEyeY: random(-1, 1),
  }
}

function startDrift() {
  if (driftTimer !== null) {
    return
  }
  refreshDrift()
  driftTimer = window.setInterval(refreshDrift, 1100)
}

function stopDrift() {
  clearTimer(driftTimer)
  driftTimer = null
  resetDrift()
}

function updateLookFromActiveInput() {
  if (!entranceReady.value) {
    return
  }
  const root = sceneRef.value
  const active = document.activeElement as HTMLElement | null
  if (!root || !active) {
    return
  }
  if (!(active instanceof HTMLInputElement || active instanceof HTMLTextAreaElement)) {
    return
  }
  const sceneRect = root.getBoundingClientRect()
  const targetRect = active.getBoundingClientRect()
  const sceneCenterX = sceneRect.left + sceneRect.width / 2
  const sceneCenterY = sceneRect.top + sceneRect.height / 2
  const targetCenterX = targetRect.left + targetRect.width / 2
  const targetCenterY = targetRect.top + targetRect.height / 2
  const nextX = clamp((targetCenterX - sceneCenterX) / 20, -15, 15)
  const nextY = clamp((targetCenterY - sceneCenterY) / 30, -10, 10)
  updateAxis(inputLookX, nextX)
  updateAxis(inputLookY, nextY)
}

function runInputTrackingFrame() {
  if (!inputTrackingActive) {
    return
  }
  updateLookFromActiveInput()
  inputTrackingRafId = window.requestAnimationFrame(runInputTrackingFrame)
}

function startInputTracking() {
  if (inputTrackingActive || !entranceReady.value) {
    return
  }
  inputTrackingActive = true
  runInputTrackingFrame()
}

function stopInputTracking() {
  inputTrackingActive = false
  clearAnimationFrame(inputTrackingRafId)
  inputTrackingRafId = null
}

function stopTextMutualGaze() {
  clearTimer(textMutualGazeTimer)
  textMutualGazeTimer = null
  textMutualGaze.value = false
}

function triggerTextMutualGaze() {
  stopTextMutualGaze()
  textMutualGaze.value = true
  textMutualGazeTimer = window.setTimeout(() => {
    textMutualGaze.value = false
    updateLookFromActiveInput()
  }, 800)
}

function scheduleBlink(target: typeof purpleBlinking, base: number) {
  const loop = () => {
    const timer = window.setTimeout(() => {
      target.value = true
      const closeTimer = window.setTimeout(() => {
        target.value = false
        loop()
      }, 140)
      blinkTimers.push(closeTimer)
    }, random(base, base + 3200))
    blinkTimers.push(timer)
  }
  loop()
}

function schedulePeek(refFlag: typeof purplePeekWhenAway, timeoutRef: 'purple' | 'black') {
  const next = window.setTimeout(() => {
    if (!isLookingAway.value || isError.value || isSuccess.value) {
      return
    }
    refFlag.value = true
    const release = window.setTimeout(() => {
      refFlag.value = false
      schedulePeek(refFlag, timeoutRef)
    }, 520)
    if (timeoutRef === 'purple') {
      purplePeekTimer = release
    } else {
      blackPeekTimer = release
    }
  }, random(5200, 9800))
  if (timeoutRef === 'purple') {
    purplePeekTimer = next
  } else {
    blackPeekTimer = next
  }
}

function stopPeek() {
  clearTimer(purplePeekTimer)
  clearTimer(blackPeekTimer)
  purplePeekTimer = null
  blackPeekTimer = null
  purplePeekWhenAway.value = false
  blackPeekWhenAway.value = false
}

function trackPointer(event: MouseEvent) {
  if (!entranceReady.value) {
    return
  }
  pointerPendingX = event.clientX
  pointerPendingY = event.clientY
  if (pointerRafId !== null) {
    return
  }
  pointerRafId = window.requestAnimationFrame(() => {
    pointerRafId = null
    applyPointerLook()
  })
}

function applyPointerLook() {
  if (!entranceReady.value) {
    return
  }
  const root = sceneRef.value
  if (!root || isError.value || isSuccess.value) {
    return
  }
  const rect = root.getBoundingClientRect()
  const cx = rect.left + rect.width / 2
  const cy = rect.top + rect.height / 2
  const nextX = clamp((pointerPendingX - cx) / 20, -15, 15)
  const nextY = clamp((pointerPendingY - cy) / 30, -10, 10)
  updateAxis(lookX, nextX)
  updateAxis(lookY, nextY)
}

watch(
  () => props.focusedField,
  (focusedField) => {
    if (focusedField === 'text') {
      startDrift()
      startInputTracking()
      triggerTextMutualGaze()
      return
    }
    stopDrift()
    stopInputTracking()
    stopTextMutualGaze()
  },
  { immediate: true },
)

watch(
  () => isLookingAway.value,
  (lookingAway) => {
    stopPeek()
    if (lookingAway) {
      schedulePeek(purplePeekWhenAway, 'purple')
      schedulePeek(blackPeekWhenAway, 'black')
    }
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('mousemove', trackPointer, { passive: true })
  scheduleBlink(purpleBlinking, 2400)
  scheduleBlink(blackBlinking, 1900)
  entranceTimer = window.setTimeout(() => {
    entranceReady.value = true
    if (props.focusedField === 'text') {
      startInputTracking()
    }
  }, 1550)
})

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', trackPointer)
  stopDrift()
  stopPeek()
  stopInputTracking()
  stopTextMutualGaze()
  clearAnimationFrame(pointerRafId)
  pointerRafId = null
  clearTimer(entranceTimer)
  entranceTimer = null
  while (blinkTimers.length) {
    const timer = blinkTimers.pop()
    if (typeof timer === 'number') {
      window.clearTimeout(timer)
    }
  }
})

function transformStyle(x: number, y: number, rotate = 0) {
  return `translate(${x}px, ${y}px) rotate(${rotate}deg)`
}

const purpleStyle = computed(() => {
  if (isShowingPassword.value) {
    return { transform: 'skewX(0deg)', height: '370px' }
  }
  if (isLookingAway.value) {
    return { transform: 'skewX(-14deg) translateX(-20px)', height: '410px' }
  }
  if (isError.value) {
    return { transform: 'skewX(0deg)', height: '370px' }
  }
  if (isTyping.value) {
    if (textMutualGaze.value) {
      return {
        transform: `skewX(${bodySkew.value - 12 + drift.value.pSkew}deg) translateX(${40 + drift.value.pX}px)`,
        height: '410px',
      }
    }
    return {
      transform: `skewX(${activeBodySkew.value - 9 + drift.value.pSkew}deg) translateX(${26 + drift.value.pX}px)`,
      height: '410px',
    }
  }
  return {
    transform: `skewX(${bodySkew.value}deg)`,
    height: '370px',
  }
})

const blackStyle = computed(() => {
  if (isShowingPassword.value) {
    return { transform: 'skewX(0deg)' }
  }
  if (isLookingAway.value) {
    return { transform: 'skewX(12deg) translateX(-10px)' }
  }
  if (isError.value) {
    return { transform: 'skewX(0deg)' }
  }
  if (isTyping.value) {
    if (textMutualGaze.value) {
      return {
        transform: `skewX(${bodySkew.value * 1.5 + drift.value.bSkew + 10}deg) translateX(${20 + drift.value.bX}px)`,
      }
    }
    return {
      transform: `skewX(${activeBodySkew.value * 1.5 + 6 + drift.value.bSkew}deg) translateX(${12 + drift.value.bX}px)`,
    }
  }
  return {
    transform: `skewX(${bodySkew.value}deg)`,
  }
})

const orangeStyle = computed(() => {
  if (isShowingPassword.value || isError.value) {
    return { transform: 'skewX(0deg)' }
  }
  return { transform: `skewX(${bodySkew.value}deg)` }
})

const yellowStyle = computed(() => {
  if (isShowingPassword.value || isError.value) {
    return { transform: 'skewX(0deg)' }
  }
  return { transform: `skewX(${bodySkew.value}deg)` }
})

const purpleEyesStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-25, -5) }
  if (isLookingAway.value) return { transform: transformStyle(-25, -15) }
  if (isError.value) return { transform: transformStyle(-15, 15) }
  if (isTyping.value) {
    if (textMutualGaze.value) {
      return { transform: transformStyle(10 + drift.value.pEyeX, 25 + drift.value.pEyeY) }
    }
    return { transform: transformStyle(activeLookX.value + drift.value.pEyeX, activeLookY.value + drift.value.pEyeY) }
  }
  return { transform: transformStyle(activeLookX.value, activeLookY.value) }
})

const purplePupilStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-4, -4) }
  if (isLookingAway.value) {
    const x = purplePeekWhenAway.value ? 6 : -5
    const y = purplePeekWhenAway.value ? 0 : -5
    return { transform: transformStyle(x, y) }
  }
  if (isError.value) return { transform: transformStyle(-3, 4) }
  if (isTyping.value) {
    if (textMutualGaze.value) {
      return { transform: transformStyle(3, 4) }
    }
    return { transform: transformStyle(activePupilX.value, activePupilY.value) }
  }
  return { transform: transformStyle(pupilX.value, pupilY.value) }
})

const purpleMouthStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-25, -5) }
  if (isLookingAway.value) return { transform: transformStyle(-25, -15) }
  if (isError.value) return { transform: transformStyle(-15, 15) }
  if (isTyping.value) {
    if (textMutualGaze.value) {
      return { transform: transformStyle(10 + drift.value.pEyeX, 25 + drift.value.pEyeY) }
    }
    return { transform: transformStyle(activeLookX.value + drift.value.pEyeX, activeLookY.value + drift.value.pEyeY) }
  }
  return { transform: transformStyle(activeLookX.value, activeLookY.value) }
})

const blackEyesStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-16, -4) }
  if (isLookingAway.value) return { transform: transformStyle(-16, -12) }
  if (isError.value) return { transform: transformStyle(-11, 8) }
  if (isTyping.value) {
    if (textMutualGaze.value) {
      return { transform: transformStyle(6 + drift.value.bEyeX, -20 + drift.value.bEyeY) }
    }
    return {
      transform: transformStyle(activeLookX.value * 0.85 + drift.value.bEyeX, activeLookY.value * 0.85 + drift.value.bEyeY),
    }
  }
  return { transform: transformStyle(activeLookX.value * 0.85, activeLookY.value * 0.85) }
})

const blackPupilStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-4, -4) }
  if (isLookingAway.value) {
    const x = blackPeekWhenAway.value ? 5 : -4
    const y = blackPeekWhenAway.value ? 0 : -5
    return { transform: transformStyle(x, y) }
  }
  if (isError.value) return { transform: transformStyle(-3, 4) }
  if (isTyping.value) {
    if (textMutualGaze.value) {
      return { transform: transformStyle(0, -4) }
    }
    return { transform: transformStyle(activePupilX.value * 0.8, activePupilY.value * 0.8) }
  }
  return { transform: transformStyle(pupilX.value * 0.8, pupilY.value * 0.8) }
})

const orangeEyesStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-32, -5) }
  if (isLookingAway.value) return { transform: transformStyle(-32, -15) }
  if (isError.value) return { transform: transformStyle(-22, 5) }
  return { transform: transformStyle(lookX.value, lookY.value) }
})

const orangePupilStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-5, -4) }
  if (isLookingAway.value) return { transform: transformStyle(-5, -5) }
  if (isError.value) return { transform: transformStyle(-3, 4) }
  return { transform: transformStyle(pupilX.value, pupilY.value) }
})

const orangeMouthStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-15, -3) }
  if (isLookingAway.value) return { transform: transformStyle(-15, -10) }
  if (isError.value) return { transform: transformStyle(lookX.value - 10, 10) }
  return { transform: transformStyle(lookX.value, lookY.value) }
})

const yellowEyesStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-32, -5) }
  if (isLookingAway.value) return { transform: transformStyle(-32, -10) }
  if (isError.value) return { transform: transformStyle(-17, 5) }
  return { transform: transformStyle(lookX.value, lookY.value) }
})

const yellowPupilStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-5, -4) }
  if (isLookingAway.value) return { transform: transformStyle(-5, -5) }
  if (isError.value) return { transform: transformStyle(-3, 4) }
  return { transform: transformStyle(pupilX.value, pupilY.value) }
})

const yellowMouthStyle = computed(() => {
  if (isShowingPassword.value) return { transform: transformStyle(-30, 0) }
  if (isLookingAway.value) return { transform: transformStyle(-25, -10) }
  if (isError.value) return { transform: transformStyle(-10, 4, -8) }
  return { transform: transformStyle(lookX.value, lookY.value) }
})
</script>

<style scoped>
.characters-wrapper {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 100%;
  padding-bottom: 40px;
}

.characters-scene {
  position: relative;
  width: 480px;
  height: 360px;
  contain: layout style;
  overflow: visible;
}

.character {
  position: absolute;
  bottom: 0;
  opacity: 0;
  transform-origin: bottom center;
  transition:
    transform 0.52s cubic-bezier(0.22, 1, 0.36, 1),
    height 0.56s ease;
  will-change: transform, height, bottom, opacity;
  backface-visibility: hidden;
  transform: translateZ(0);
}

.char-purple {
  left: 60px;
  width: 170px;
  height: 370px;
  border-radius: 10px 10px 0 0;
  background: #6c3ff5;
  z-index: 1;
  animation: characterEntrance 1.08s cubic-bezier(0.2, 0.84, 0.26, 1) both;
  animation-delay: 0.15s;
}

.char-black {
  left: 220px;
  width: 115px;
  height: 290px;
  border-radius: 8px 8px 0 0;
  background: #2d2d2d;
  z-index: 2;
  animation: characterEntrance 1.08s cubic-bezier(0.2, 0.84, 0.26, 1) both;
  animation-delay: 0.25s;
}

.char-orange {
  left: 0;
  width: 230px;
  height: 190px;
  border-radius: 115px 115px 0 0;
  background: #ff9b6b;
  z-index: 3;
  animation: characterEntrance 1.08s cubic-bezier(0.2, 0.84, 0.26, 1) both;
  animation-delay: 0.05s;
}

.char-yellow {
  left: 290px;
  width: 135px;
  height: 215px;
  border-radius: 68px 68px 0 0;
  background: #e8d754;
  z-index: 4;
  animation: characterEntrance 1.08s cubic-bezier(0.2, 0.84, 0.26, 1) both;
  animation-delay: 0.35s;
}

@keyframes characterEntrance {
  0% {
    bottom: -340px;
    opacity: 0;
    animation-timing-function: cubic-bezier(0.2, 0.84, 0.26, 1);
  }
  52% {
    bottom: 18px;
    opacity: 1;
    animation-timing-function: cubic-bezier(0.55, 0.05, 0.68, 0.19);
  }
  72% {
    bottom: -10px;
    animation-timing-function: cubic-bezier(0.2, 0.84, 0.26, 1);
  }
  88% {
    bottom: 4px;
    animation-timing-function: cubic-bezier(0.55, 0.05, 0.68, 0.19);
  }
  100% {
    bottom: 0;
    opacity: 1;
  }
}

.eyes {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  gap: 20px;
  transition: transform 0.46s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform;
  backface-visibility: hidden;
}

.char-purple .eyes {
  left: 45px;
  top: 40px;
  gap: 28px;
}

.char-black .eyes {
  left: 26px;
  top: 32px;
  gap: 20px;
}

.char-orange .eyes {
  left: 82px;
  top: 90px;
  gap: 28px;
}

.char-yellow .eyes {
  left: 52px;
  top: 40px;
  gap: 20px;
}

.eyeball {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: height 0.15s ease;
  will-change: height;
}

.char-black .eyeball {
  width: 16px;
  height: 16px;
}

.eyeball.blink {
  height: 2px;
}

.pupil,
.bare-pupil {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #2d2d2d;
  transition: transform 0.18s linear;
  will-change: transform;
  backface-visibility: hidden;
}

.char-black .pupil {
  width: 6px;
  height: 6px;
}

.bare-eyes .bare-pupil {
  width: 12px;
  height: 12px;
}

.purple-mouth,
.yellow-mouth,
.orange-mouth {
  position: absolute;
  will-change: transform, width, height, border-radius;
  transition: all 0.46s cubic-bezier(0.22, 1, 0.36, 1);
  backface-visibility: hidden;
}

.purple-mouth {
  left: 76px;
  top: 66px;
  width: 12px;
  height: 6px;
  border-radius: 0 0 6px 6px;
  background: #2d2d2d;
}

.yellow-mouth {
  left: 40px;
  top: 88px;
  width: 50px;
  height: 4px;
  border-radius: 2px;
  background: #2d2d2d;
}

.orange-mouth {
  left: 90px;
  top: 120px;
  width: 28px;
  height: 14px;
  border: 4px solid #2d2d2d;
  border-top: none;
  border-radius: 0 0 14px 14px;
}

.purple-mouth.error-face {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 4px solid #2d2d2d;
  background: transparent;
}

.orange-mouth.error-face {
  border-top: 4px solid #2d2d2d;
  border-bottom: none;
  border-radius: 14px 14px 0 0;
}

.yellow-mouth.error-face {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 4px solid #2d2d2d;
  background: transparent;
}

.purple-mouth.success-face {
  width: 20px;
  height: 10px;
  border-radius: 0 0 10px 10px;
}

.orange-mouth.success-face {
  width: 34px;
  height: 18px;
  border: 5px solid #2d2d2d;
  border-top: none;
  border-radius: 0 0 18px 18px;
}

.yellow-mouth.success-face {
  height: 16px;
  border: 4px solid #2d2d2d;
  border-top: none;
  border-radius: 0 0 16px 16px;
  background: transparent;
}

@keyframes jumpHappy {
  0%,
  100% {
    transform: translateY(0) scaleY(1);
  }
  30% {
    transform: translateY(-30px) scaleY(1.05);
  }
  70% {
    transform: translateY(-10px) scaleY(0.95);
  }
}

.success-jump {
  animation: jumpHappy 0.6s cubic-bezier(0.25, 0.8, 0.25, 1);
}

@media (max-width: 1200px) {
  .characters-scene {
    width: 420px;
    height: 320px;
    transform: scale(0.9);
    transform-origin: bottom center;
  }
}
</style>
