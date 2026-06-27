<template>
  <div
    v-if="tabbarEnable"
    class="layout-tabbar flex items-end justify-between border-b border-border bg-header"
    :style="tabbarStyle"
  >
    <button
      v-if="isOverflowing"
      class="tabbar-scroll-btn tabbar-scroll-btn--left"
      type="button"
      :disabled="!canScrollLeft"
      @click="scrollBy(-360)"
    >
      <el-icon><ArrowLeft /></el-icon>
    </button>

    <div
      ref="scrollerRef"
      class="tabbar-scroller"
      @scroll="updateScrollState"
      @wheel="handleWheel"
    >
      <div class="tabbar-wrapper" :class="`tabbar-wrapper--${tabbarStyleType || 'chrome'}`">
        <div
          v-for="tab in tabs"
          :key="tab.path"
          class="tabbar-item"
          :class="{
            active: route.path === tab.path,
            pinned: tab.pinned,
            'is-dragging': dragState.draggingPath === tab.path,
            'is-drag-over': dragState.overPath === tab.path && dragState.draggingPath !== tab.path,
          }"
          :data-tab-path="tab.path"
          draggable="true"
          @click="handleClick(tab)"
          @contextmenu.prevent="handleContextMenu($event, tab)"
          @dragstart="handleDragStart($event, tab)"
          @dragover="handleDragOver($event, tab)"
          @drop="handleDrop($event, tab)"
          @dragend="handleDragEnd"
          @mouseup="handleMouseUp($event, tab)"
        >
          <span class="tabbar-item-inner">
            <el-icon v-if="tabbarShowIcon" class="tabbar-item-icon">
              <component :is="resolveTabIcon(tab)" />
            </el-icon>
            <span class="tabbar-item-text">{{ tab.title }}</span>
            <el-icon v-if="tab.pinned" class="tabbar-item-pin">
              <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="1em" height="1em">
                <path fill="currentColor" d="M640 192v256l128 192v64H544v256L512 992l-32-32V704H256v-64l128-192V192h-64v-64h448v64h-64zM384 192v271.04l-113.344 170.048L281.344 640h461.312l10.688-6.912L640 463.04V192H384z"/>
              </svg>
            </el-icon>
            <el-icon
              v-else
              class="tabbar-item-close"
              @click.stop="handleClose(tab)"
            >
              <Close />
            </el-icon>
          </span>
        </div>
      </div>
    </div>

    <div class="flex items-center px-1 border-l border-border flex-shrink-0 gap-0.5" :style="tabbarActionStyle">
      <button
        v-if="isOverflowing"
        class="tabbar-scroll-btn tabbar-scroll-btn--right"
        type="button"
        :disabled="!canScrollRight"
        @click="scrollBy(360)"
      >
        <el-icon><ArrowRight /></el-icon>
      </button>

      <el-dropdown v-if="tabbarShowMore !== false" trigger="click" @command="handleCommand">
        <div class="tabbar-action-btn">
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="refresh"><el-icon><Refresh /></el-icon> 重新加载</el-dropdown-item>
            <el-dropdown-item command="closeOther"><el-icon><Close /></el-icon> 关闭其他标签页</el-dropdown-item>
            <el-dropdown-item command="closeAll"><el-icon><Close /></el-icon> 关闭全部标签页</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <div
        v-if="tabbarShowMaximize !== false"
        class="tabbar-action-btn"
        :class="{ 'is-active': isFullscreen }"
        :title="isFullscreen ? '退出页面全屏' : '页面全屏'"
        @click="emit('toggle-fullscreen')"
      >
        <el-icon v-if="!isFullscreen"><FullScreen /></el-icon>
        <el-icon v-else><ScaleToOriginal /></el-icon>
      </div>
    </div>

    <!-- 右键菜单 -->
    <ul
      v-if="contextMenu.visible"
      class="fixed z-[1000] min-w-[160px] rounded-lg border border-border bg-popover shadow-lg py-1 text-sm"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      @click.stop
    >
      <li
        v-for="menu in contextMenus"
        :key="menu.key"
        class="context-menu-item"
        :class="{ 'is-disabled': menu.disabled }"
        @click="handleContextCommand(menu.key)"
      >
        <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
        {{ menu.text }}
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  Close,
  Document,
  FullScreen,
  Refresh,
  ScaleToOriginal,
} from '@element-plus/icons-vue'
import { useTabbarStore, type TabItem } from '@/stores/tabbar'
import { useTabbar } from '@/layouts/tabbar/use-tabbar'

const props = defineProps<{
  tabbarEnable: boolean
  tabbarShowIcon?: boolean
  tabbarMiddleClickToClose?: boolean
  tabbarStyleType?: 'chrome' | 'plain' | 'card' | 'brisk'
  tabbarShowMore?: boolean
  tabbarShowMaximize?: boolean
  isFullscreen?: boolean
  height?: number
}>()

const emit = defineEmits<{
  'toggle-fullscreen': []
  'refresh': []
}>()

const route = useRoute()
const router = useRouter()
const tabbarStore = useTabbarStore()
const {
  createContextMenus,
  currentTabs,
  handleClose: handleTabClose,
} = useTabbar()

const tabs = computed(() => currentTabs.value as TabItem[])
const tabbarHeight = computed(() => `${props.height ?? 46}px`)
const tabbarStyle = computed(() => ({
  height: tabbarHeight.value,
  transition: 'height 220ms cubic-bezier(0.2, 0, 0, 1)',
}))
const tabbarActionStyle = computed(() => ({
  height: tabbarHeight.value,
}))
const scrollerRef = ref<HTMLDivElement | null>(null)
const isOverflowing = ref(false)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)
const tabbarMiddleClickToCloseVal = computed(
  () => props.tabbarMiddleClickToClose ?? true,
)

const contextMenu = ref({
  visible: false,
  x: 0,
  y: 0,
})
const contextMenuTab = ref<TabItem | null>(null)
const contextMenus = computed(() => {
  const tab = contextMenuTab.value
  if (!tab) return []
  return createContextMenus(tab as any)
})

const dragState = ref({
  draggingPath: '',
  overPath: '',
})

watch(
  () => route.path,
  () => {
    nextTick(() => {
      scrollActiveIntoView()
      updateScrollState()
    })
  },
  { immediate: true },
)

watch(tabs, () => {
  nextTick(() => {
    updateScrollState()
    scrollActiveIntoView()
  })
}, { deep: true })

function updateScrollState() {
  const scroller = scrollerRef.value
  if (!scroller) return
  const maxScrollLeft = scroller.scrollWidth - scroller.clientWidth
  isOverflowing.value = maxScrollLeft > 1
  canScrollLeft.value = scroller.scrollLeft > 1
  canScrollRight.value = scroller.scrollLeft < maxScrollLeft - 1
}

function scrollBy(distance: number) {
  scrollerRef.value?.scrollBy({ left: distance, behavior: 'smooth' })
  window.setTimeout(updateScrollState, 220)
}

function handleWheel(event: WheelEvent) {
  const scroller = scrollerRef.value
  if (!scroller || !isOverflowing.value) return
  const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
  if (!delta) return
  event.preventDefault()
  scroller.scrollLeft += delta
  updateScrollState()
}

function scrollActiveIntoView() {
  const scroller = scrollerRef.value
  if (!scroller) return
  const active = scroller.querySelector<HTMLElement>('.tabbar-item.active')
  active?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  updateScrollState()
}

function handleClick(tab: TabItem) {
  if (route.path !== tab.path) {
    void router.push(tab.path)
  }
}

function handleClose(tab: TabItem) {
  handleTabClose(tab.path)
  if (route.path === tab.path) {
    const nextTab = tabs.value.find((t) => t.path !== tab.path)
    if (nextTab) {
      void router.push(nextTab.path)
    } else {
      void router.push('/dashboard')
    }
  }
}

function handleCommand(command: string) {
  if (command === 'refresh') {
    emit('refresh')
  } else if (command === 'closeOther') {
    tabbarStore.closeOtherTabs(route.path)
  } else if (command === 'closeAll') {
    tabbarStore.closeAllTabs()
    void router.push('/dashboard')
  }
}

function handleContextMenu(event: MouseEvent, tab: TabItem) {
  contextMenuTab.value = tab
  contextMenu.value = {
    visible: true,
    x: event.clientX,
    y: event.clientY,
  }
}

function handleContextCommand(command: string) {
  const tab = contextMenuTab.value
  contextMenu.value.visible = false
  if (!tab) return

  if (command === 'refresh') {
    emit('refresh')
  } else if (command === 'close') {
    handleClose(tab)
  } else if (command === 'closeOther') {
    tabbarStore.closeOtherTabs(tab.path)
    if (route.path !== tab.path) {
      void router.push(tab.path)
    }
  } else if (command === 'closeAll') {
    tabbarStore.closeAllTabs()
    void router.push('/dashboard')
  } else if (command === 'pin') {
    if (tab.pinned) {
      tabbarStore.unpinTab(tab.path)
    } else {
      tabbarStore.pinTab(tab.path)
    }
  } else if (command === 'close-left') {
    tabbarStore.closeLeftTabs(tab.path)
  } else if (command === 'close-right') {
    tabbarStore.closeRightTabs(tab.path)
  }
}

function closeContextMenu() {
  contextMenu.value.visible = false
}

function resolveTabIcon(tab: TabItem) {
  if (tab.path === '/dashboard') return FullScreen
  if (tab.icon) {
    const iconMap: Record<string, any> = {
      User: Document,
      Connection: Document,
      OfficeBuilding: Document,
      Flag: Document,
      Histogram: Document,
      Setting: Document,
      Monitor: Document,
    }
    if (iconMap[tab.icon]) return iconMap[tab.icon]
  }
  if (tab.title.includes('用户')) return Document
  if (tab.title.includes('个人') || tab.title.includes('账号')) return Document
  if (tab.title.includes('角色')) return Document
  if (tab.title.includes('部门')) return Document
  if (tab.title.includes('租户')) return Document
  if (tab.title.includes('审计') || tab.title.includes('分析')) return Document
  if (tab.title.includes('设置') || tab.title.includes('配置')) return Document
  return Document
}

function handleDragStart(event: DragEvent, tab: TabItem) {
  if (tab.pinned) {
    event.preventDefault()
    return
  }
  dragState.value.draggingPath = tab.path
  event.dataTransfer?.setData('text/plain', tab.path)
  event.dataTransfer?.setData('application/tab-path', tab.path)
  event.dataTransfer!.effectAllowed = 'move'
}

function handleDragOver(event: DragEvent, tab: TabItem) {
  event.preventDefault()
  if (!dragState.value.draggingPath || dragState.value.draggingPath === tab.path) return
  dragState.value.overPath = tab.path
  event.dataTransfer!.dropEffect = 'move'
}

function handleDrop(event: DragEvent, tab: TabItem) {
  event.preventDefault()
  const fromPath = event.dataTransfer?.getData('application/tab-path') || dragState.value.draggingPath
  const toPath = tab.path
  dragState.value = { draggingPath: '', overPath: '' }
  if (!fromPath || !toPath || fromPath === toPath) return

  const current = tabs.value.slice()
  const fromIndex = current.findIndex((t) => t.path === fromPath)
  const toIndex = current.findIndex((t) => t.path === toPath)
  if (fromIndex === -1 || toIndex === -1) return

  const [moved] = current.splice(fromIndex, 1)
  current.splice(toIndex, 0, moved)
  tabbarStore.sortTabs(current)
}

function handleDragEnd() {
  dragState.value = { draggingPath: '', overPath: '' }
}

function handleMouseUp(event: MouseEvent, tab: TabItem) {
  // 中键关闭
  if (
    tabbarMiddleClickToCloseVal.value &&
    event.button === 1 &&
    tab &&
    !tab.pinned
  ) {
    event.preventDefault()
    handleClose(tab)
  }
}

onMounted(() => {
  window.addEventListener('resize', updateScrollState)
  document.addEventListener('click', closeContextMenu)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateScrollState)
  document.removeEventListener('click', closeContextMenu)
})
</script>

<style scoped lang="scss">
.tabbar-scroller {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  white-space: nowrap;
  height: 100%;
  min-width: 0;
  padding-left: 0;
  overscroll-behavior: contain;
  scrollbar-width: none;
  -ms-overflow-style: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.tabbar-wrapper {
  display: flex;
  align-items: center;
  height: 100%;
  width: max-content;
  min-width: 100%;
  flex-wrap: nowrap;
  padding-left: 0;
  padding-top: 0;
}

.tabbar-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  height: 100%;
  max-width: 168px;
  padding: 0;
  appearance: none;
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;

  .tabbar-item-inner {
    display: inline-flex;
    align-items: center;
    min-width: 0;
    gap: 5px;
    width: 100%;
    position: relative;
    z-index: 1;
    height: 100%;
    padding: 0 12px 0 11px;
  }

  .tabbar-item-icon {
    flex: 0 0 auto;
    font-size: 15px;
    color: currentColor;
    transform: translateY(-0.5px);
  }

  .tabbar-item-text {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1;
    transform: translateY(0.5px);
  }

  .tabbar-item-pin {
    flex: 0 0 auto;
    font-size: 12px;
    margin-left: 1px;
    transform: rotate(45deg);
    color: hsl(var(--muted-foreground));
  }

  .tabbar-item-close {
    flex: 0 0 auto;
    width: 15px;
    height: 15px;
    font-size: 12px;
    border-radius: 50%;
    transition: all 0.2s;
    opacity: 0.72;

    &:hover {
      background-color: hsl(var(--foreground) / 0.12);
      color: hsl(var(--foreground));
    }
  }
}

.tabbar-wrapper--chrome {
  .tabbar-item {
    margin-right: -12px;
    color: hsl(var(--foreground) / 0.8);
    transition: color 0.2s ease, transform 0.2s ease;

    &::before {
      content: '';
      position: absolute;
      inset: 8px 0 0;
      border-radius: 10px 10px 0 0;
      background: transparent;
      border: 1px solid transparent;
      border-bottom: 0;
      z-index: 0;
      transition: background-color 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
    }

    &:hover {
      color: hsl(var(--primary));
      transform: translateY(-0.5px);

      &::before {
        background: hsl(var(--accent-lighter));
        border-color: hsl(var(--border));
      }

      .tabbar-item-close {
        opacity: 1;
      }
    }

    &.active {
      color: hsl(var(--primary));
      z-index: 2;
      font-weight: 600;

      &::before {
        background-color: hsl(var(--background));
        border-color: hsl(var(--border));
        box-shadow: 0 -1px 0 hsl(var(--background));
      }

      .tabbar-item-close {
        opacity: 1;
      }
    }

    &.pinned .tabbar-item-pin {
      opacity: 1;
    }

    &.is-dragging {
      opacity: 0.5;
    }

    &.is-drag-over {
      &::before {
        border-color: hsl(var(--primary) / 0.28);
      }
    }
  }
}

.tabbar-wrapper--plain {
  .tabbar-item {
    border-radius: 0;
    background: transparent;
    border: none;
    border-bottom: 2px solid transparent;
    color: hsl(var(--foreground) / 0.85);
    transition: border-color 0.2s, color 0.2s;

    &:hover {
      color: hsl(var(--primary));
      background: transparent;

      .tabbar-item-close {
        opacity: 1;
      }
    }

    &.active {
      background: transparent;
      color: hsl(var(--primary));
      font-weight: 600;
      border-bottom-color: hsl(var(--primary));

      .tabbar-item-close {
        opacity: 1;
      }
    }

    &.pinned .tabbar-item-pin {
      opacity: 1;
    }

    &.is-dragging {
      opacity: 0.5;
    }

    &.is-drag-over {
      border-bottom: 2px solid hsl(var(--primary));
    }
  }
}

.tabbar-wrapper--card {
  .tabbar-item {
    border-radius: 6px 6px 0 0;
    background: hsl(var(--muted) / 0.4);
    border: 1px solid hsl(var(--border));
    border-bottom: none;
    margin-right: 4px;
    color: hsl(var(--foreground) / 0.85);
    transition: background-color 0.2s, color 0.2s;

    &:hover {
      background: hsl(var(--accent));

      .tabbar-item-close {
        opacity: 1;
      }
    }

    &.active {
      background: hsl(var(--background));
      color: hsl(var(--primary));
      font-weight: 600;
      border-color: hsl(var(--border));
      margin-bottom: -1px;
      padding-bottom: 1px;
      z-index: 1;

      .tabbar-item-close {
        opacity: 1;
      }
    }

    &.pinned .tabbar-item-pin {
      opacity: 1;
    }

    &.is-dragging {
      opacity: 0.5;
    }

    &.is-drag-over {
      border-bottom: 2px solid hsl(var(--primary));
    }
  }
}

.tabbar-wrapper--brisk {
  .tabbar-item {
    border-radius: 6px 6px 0 0;
    background: transparent;
    border: none;
    color: hsl(var(--foreground) / 0.85);
    transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);

    &:hover {
      background: hsl(var(--accent));
      color: hsl(var(--primary));
      transform: translateY(-1px);

      .tabbar-item-close {
        opacity: 1;
      }
    }

    &.active {
      background: linear-gradient(
        180deg,
        hsl(var(--primary) / 0.16) 0%,
        hsl(var(--primary) / 0.08) 100%
      );
      color: hsl(var(--primary));
      font-weight: 600;

      .tabbar-item-close {
        opacity: 1;
      }
    }

    &.pinned .tabbar-item-pin {
      opacity: 1;
    }

    &.is-dragging {
      opacity: 0.5;
    }

    &.is-drag-over {
      border-bottom: 2px solid hsl(var(--primary));
    }
  }
}

.tabbar-scroll-btn {
  flex: 0 0 32px;
  width: 32px;
  height: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, opacity 0.2s ease;

  .el-icon {
    font-size: 15px;
  }

  &:hover:not(:disabled) {
    color: hsl(var(--primary));
    background: hsl(var(--accent));
  }

  &:disabled {
    cursor: default;
    opacity: 0.36;
  }
}

.tabbar-scroll-btn--left {
  border-right: 1px solid hsl(var(--border));
}

.tabbar-scroll-btn--right {
  height: 30px;
  border-radius: 6px;
}

.tabbar-action-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: hsl(var(--foreground) / 0.85);
  border-radius: 6px;
  transition: all 0.2s;
  background: transparent;

  &:hover,
  &.is-active {
    color: hsl(var(--primary));
    background: hsl(var(--accent));
  }

  .el-icon {
    font-size: 15px;
    outline: none;
  }
}

.layout-tabbar {
  background: hsl(var(--header));
  backdrop-filter: blur(10px);
  overflow: hidden;
  padding-right: 4px;
  border-bottom-color: hsl(var(--border) / 0.9);
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  color: hsl(var(--popover-foreground));
  cursor: pointer;
  transition: background-color 0.15s;

  &:hover {
    background-color: hsl(var(--accent));
  }

  .el-icon {
    font-size: 14px;
  }

  &.is-disabled {
    opacity: 0.5;
    pointer-events: none;
  }
}
</style>
