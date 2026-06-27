<script setup lang="ts">
import { computed, onBeforeUnmount, provide, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppNavItem from '@/layouts/navigation/AppNavItem.vue'
import { menuContextKey, type MenuContext, type NavLink } from '@/layouts/navigation/menu-context'

interface Props {
  menus?: NavLink[]
  activePath?: string
  theme?: string
  variant?: 'panel' | 'rail'
  collapse?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  menus: () => [],
  activePath: '',
  theme: 'light',
  variant: 'panel',
  collapse: false,
})

const emit = defineEmits<{
  select: [path: string]
}>()

const route = useRoute()
const openedMenus = ref<Set<string>>(new Set())
const activeRootId = ref('')
const previewRootId = ref('')
const rememberedChildPathMap = new Map<string, string>()
const previewTimer = ref<number | null>(null)
const restoreTimer = ref<number | null>(null)

const currentPath = computed(() => props.activePath || route.path)
const topLevelMenus = computed(() => props.menus.filter((item) => item.to || item.children.length > 0))

const rootMenu = computed(() => {
  if (!topLevelMenus.value.length) return null
  const preferredRootId = previewRootId.value || activeRootId.value
  return topLevelMenus.value.find((item) => item.id === preferredRootId) ?? findRootMenu(topLevelMenus.value, currentPath.value) ?? topLevelMenus.value[0]
})

const sideMenus = computed(() => rootMenu.value?.children ?? [])
const sideMenuCount = computed(() => sideMenus.value.length)

watch(
  [topLevelMenus, currentPath],
  ([menus, path]) => {
    const matchedRoot = findRootMenu(menus, path) ?? menus[0] ?? null
    activeRootId.value = matchedRoot?.id ?? ''
    if (matchedRoot?.id && path) {
      rememberedChildPathMap.set(matchedRoot.id, path)
    }
    if (!previewRootId.value || previewRootId.value === activeRootId.value) {
      previewRootId.value = ''
    }
    openedMenus.value = collectOpenMenus(matchedRoot?.children ?? [], path)
  },
  { immediate: true },
)

function clearRailTimers() {
  if (previewTimer.value !== null) {
    window.clearTimeout(previewTimer.value)
    previewTimer.value = null
  }
  if (restoreTimer.value !== null) {
    window.clearTimeout(restoreTimer.value)
    restoreTimer.value = null
  }
}

function containsPath(item: NavLink, path: string): boolean {
  if (item.to === path) return true
  return item.children.some((child) => containsPath(child, path))
}

function findRootMenu(menus: NavLink[], path: string): NavLink | null {
  return menus.find((menu) => containsPath(menu, path)) ?? null
}

function collectOpenMenus(items: NavLink[], path: string) {
  const next = new Set<string>()
  const walk = (nodes: NavLink[]) => {
    for (const item of nodes) {
      if (item.children.some((child) => containsPath(child, path))) {
        next.add(item.id)
      }
      walk(item.children)
    }
  }
  walk(items)
  return next
}

function toggleMenu(id: string) {
  const next = new Set(openedMenus.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  openedMenus.value = next
}

function setMenuOpen(id: string, open: boolean) {
  const next = new Set(openedMenus.value)
  if (open) {
    next.add(id)
  } else {
    next.delete(id)
  }
  openedMenus.value = next
}

function syncOpenedMenus(menu: NavLink | null, path: string) {
  openedMenus.value = collectOpenMenus(menu?.children ?? [], path)
}

function selectMenu(path: string) {
  if (path) {
    emit('select', path)
  }
}

provide(
  menuContextKey,
  {
    get collapse() {
      return false
    },
    get activePath() {
      return currentPath.value
    },
    get openedMenus() {
      return openedMenus.value
    },
    toggleMenu,
    setMenuOpen,
    selectMenu,
  } as MenuContext,
)

function handleRootSelect(menu: NavLink) {
  clearRailTimers()
  previewRootId.value = ''
  activeRootId.value = menu.id
  syncOpenedMenus(menu, currentPath.value)

  if (menu.children.length === 0) {
    if (menu.to) {
      emit('select', menu.to)
    }
    return
  }

  const rememberedTarget = rememberedChildPathMap.get(menu.id)
  const fallbackTarget = menu.to || menu.children[0]?.to || findFirstChildTarget(menu.children)
  const target = rememberedTarget && containsPath(menu, rememberedTarget) ? rememberedTarget : fallbackTarget
  if (target && currentPath.value !== target) {
    emit('select', target)
  }
}

function handleRootEnter(menu: NavLink) {
  if (props.variant !== 'rail' || props.collapse) return
  if (!menu.children.length) return
  if (restoreTimer.value !== null) {
    window.clearTimeout(restoreTimer.value)
    restoreTimer.value = null
  }
  if (previewRootId.value === menu.id) return
  if (previewTimer.value !== null) {
    window.clearTimeout(previewTimer.value)
  }
  previewTimer.value = window.setTimeout(() => {
    previewRootId.value = menu.id
    syncOpenedMenus(menu, currentPath.value)
    previewTimer.value = null
  }, 56)
}

function handleRailLeave() {
  if (previewTimer.value !== null) {
    window.clearTimeout(previewTimer.value)
    previewTimer.value = null
  }
  if (restoreTimer.value !== null) {
    window.clearTimeout(restoreTimer.value)
  }
  restoreTimer.value = window.setTimeout(() => {
    previewRootId.value = ''
    syncOpenedMenus(findRootMenu(topLevelMenus.value, currentPath.value), currentPath.value)
    restoreTimer.value = null
  }, 132)
}

function findFirstChildTarget(items: NavLink[]): string {
  for (const item of items) {
    if (item.to) return item.to
    const target = findFirstChildTarget(item.children)
    if (target) return target
  }
  return ''
}

onBeforeUnmount(() => {
  clearRailTimers()
})
</script>

<template>
  <div class="mixed-menu" :class="[`mixed-menu--${variant}`, theme, { 'is-collapse': collapse }]">
    <div v-if="variant === 'rail'" class="mixed-menu__rail" @mouseleave="handleRailLeave">
      <button
        v-for="menu in topLevelMenus"
        :key="menu.id"
        type="button"
        class="mixed-menu__rail-item"
        :class="{ 'is-active': rootMenu?.id === menu.id, 'is-current': activeRootId === menu.id }"
        :title="menu.label"
        @mouseenter="handleRootEnter(menu)"
        @click="handleRootSelect(menu)"
      >
        <el-icon v-if="menu.icon" class="mixed-menu__rail-icon">
          <component :is="menu.icon" />
        </el-icon>
        <span v-if="!collapse" class="mixed-menu__rail-label">{{ menu.label }}</span>
      </button>
    </div>

    <div class="mixed-menu__panel" :class="{ 'is-hidden': variant === 'rail' && collapse, 'is-preview': !!previewRootId && previewRootId !== activeRootId }">
      <div class="mixed-menu__panel-head">
        <div class="mixed-menu__panel-copy">
          <small>{{ previewRootId && previewRootId !== activeRootId ? '预览分组' : variant === 'rail' ? '工作区导航' : '当前模块' }}</small>
          <strong>{{ rootMenu?.label || '导航菜单' }}</strong>
        </div>
        <span class="mixed-menu__panel-badge">{{ sideMenuCount }}</span>
      </div>

      <nav class="mixed-menu__panel-body" :class="{ 'is-preview': !!previewRootId && previewRootId !== activeRootId }">
        <ul v-if="sideMenus.length" class="vben-menu-list">
          <AppNavItem
            v-for="item in sideMenus"
            :key="item.id"
            :item="item"
          />
        </ul>
        <div v-else class="mixed-menu__empty">当前分组暂无可访问菜单</div>
      </nav>
    </div>
  </div>
</template>

<style scoped lang="scss">
.mixed-menu {
  display: flex;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: transparent;
  transition: background-color 0.2s ease;

  &--panel {
    .mixed-menu__panel {
      width: 100%;
      border-left: 0;
    }
  }

  &--rail {
    .mixed-menu__rail {
      width: 68px;
      min-width: 68px;
      border-right: 1px solid hsl(var(--border));
      background: linear-gradient(180deg, hsl(var(--sidebar)) 0%, hsl(var(--sidebar-deep)) 100%);
    }

    .mixed-menu__panel {
      flex: 1;
      min-width: 0;
      max-width: 220px;
    }
  }

  &.is-collapse {
    .mixed-menu__rail {
      width: 100%;
      min-width: 0;
      border-right: 0;
    }
  }
}

.mixed-menu__rail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 6px;
  overflow-y: auto;
  transition: width 0.22s cubic-bezier(0.2, 0, 0, 1), padding 0.22s cubic-bezier(0.2, 0, 0, 1);
}

.mixed-menu__rail-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  min-height: 40px;
  padding: 0 8px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    color: hsl(var(--foreground));
    background: hsl(var(--accent));
    transform: translateY(-1px);
  }

  &.is-active {
    color: hsl(var(--primary));
    background: hsl(var(--primary) / 0.12);
    box-shadow: inset 0 0 0 1px hsl(var(--primary) / 0.08);
  }

  &.is-current:not(.is-active) {
    color: hsl(var(--foreground));
    background: hsl(var(--accent) / 0.66);
  }
}

.mixed-menu__rail-icon {
  font-size: 16px;
}

.mixed-menu__rail-label {
  display: none;
}

.mixed-menu__panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: linear-gradient(180deg, hsl(var(--sidebar)) 0%, hsl(var(--sidebar-deep)) 100%);
  transition:
    max-width 0.22s cubic-bezier(0.2, 0, 0, 1),
    opacity 0.18s ease,
    transform 0.22s cubic-bezier(0.2, 0, 0, 1),
    box-shadow 0.22s ease;

  &.is-hidden {
    opacity: 0;
    transform: translateX(-8px);
    pointer-events: none;
  }

  &.is-preview {
    box-shadow: inset 1px 0 0 hsl(var(--primary) / 0.08);

    .mixed-menu__panel-copy {
      transform: translateX(1px);
    }

    .mixed-menu__panel-copy small {
      color: hsl(var(--primary));
    }

    .mixed-menu__panel-badge {
      background: hsl(var(--primary) / 0.1);
      color: hsl(var(--primary));
    }
  }
}

.mixed-menu__panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  height: 52px;
  padding: 0 16px;
  border-bottom: 1px solid hsl(var(--border));
}

.mixed-menu__panel-copy {
  display: grid;
  min-width: 0;
  gap: 4px;
  transition: transform 0.18s ease, opacity 0.18s ease;

  small {
    color: hsl(var(--muted-foreground));
    font-size: 11px;
    letter-spacing: 0.08em;
    text-transform: uppercase;
  }

  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 14px;
    font-weight: 700;
    color: hsl(var(--foreground));
  }
}

.mixed-menu__panel-badge {
  display: inline-flex;
  min-width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  border-radius: 999px;
  background: hsl(var(--accent));
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  font-weight: 700;
}

.mixed-menu__panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 0 10px;
  transition: transform 0.18s ease, opacity 0.18s ease;

  &.is-preview {
    transform: translateX(1px);
  }
}

.mixed-menu__empty {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: hsl(var(--muted-foreground));
  font-size: 13px;
}

.dark .mixed-menu__rail,
.dark .mixed-menu__panel {
  box-shadow: none;
}
</style>
