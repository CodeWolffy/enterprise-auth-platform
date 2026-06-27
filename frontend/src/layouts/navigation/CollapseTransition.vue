<script setup lang="ts">
import type { RendererElement } from 'vue'

const reset = (el: RendererElement) => {
  el.style.maxHeight = ''
  el.style.overflow = el.dataset.oldOverflow || ''
  el.style.paddingTop = el.dataset.oldPaddingTop || ''
  el.style.paddingBottom = el.dataset.oldPaddingBottom || ''
  el.style.marginTop = el.dataset.oldMarginTop || ''
  el.style.marginBottom = el.dataset.oldMarginBottom || ''
}

const on = {
  afterEnter(el: RendererElement) {
    el.style.maxHeight = ''
    el.style.overflow = el.dataset.oldOverflow || ''
  },
  afterLeave(el: RendererElement) {
    reset(el)
  },
  beforeEnter(el: RendererElement) {
    if (!el.dataset) el.dataset = {} as DOMStringMap
    el.dataset.oldPaddingTop = el.style.paddingTop
    el.dataset.oldMarginTop = el.style.marginTop
    el.dataset.oldPaddingBottom = el.style.paddingBottom
    el.dataset.oldMarginBottom = el.style.marginBottom
    el.dataset.oldOverflow = el.style.overflow
    if (el.style.maxHeight) el.dataset.oldMaxHeight = el.style.maxHeight

    el.style.maxHeight = '0'
    el.style.paddingTop = '0'
    el.style.marginTop = '0'
    el.style.paddingBottom = '0'
    el.style.marginBottom = '0'
  },
  beforeLeave(el: RendererElement) {
    if (!el.dataset) el.dataset = {} as DOMStringMap
    el.dataset.oldPaddingTop = el.style.paddingTop
    el.dataset.oldMarginTop = el.style.marginTop
    el.dataset.oldPaddingBottom = el.style.paddingBottom
    el.dataset.oldMarginBottom = el.style.marginBottom
    el.dataset.oldOverflow = el.style.overflow
    el.style.maxHeight = `${el.scrollHeight}px`
    el.style.overflow = 'hidden'
  },
  enter(el: RendererElement) {
    requestAnimationFrame(() => {
      el.style.maxHeight = `${el.scrollHeight}px`
      el.style.paddingTop = el.dataset.oldPaddingTop || ''
      el.style.paddingBottom = el.dataset.oldPaddingBottom || ''
      el.style.marginTop = el.dataset.oldMarginTop || ''
      el.style.marginBottom = el.dataset.oldMarginBottom || ''
      el.style.overflow = 'hidden'
    })
  },
  enterCancelled(el: RendererElement) {
    reset(el)
  },
  leave(el: RendererElement) {
    if (el.scrollHeight !== 0) {
      el.style.maxHeight = '0'
      el.style.paddingTop = '0'
      el.style.paddingBottom = '0'
      el.style.marginTop = '0'
      el.style.marginBottom = '0'
    }
  },
  leaveCancelled(el: RendererElement) {
    reset(el)
  },
}
</script>

<template>
  <transition name="collapse-transition" v-on="on">
    <slot />
  </transition>
</template>