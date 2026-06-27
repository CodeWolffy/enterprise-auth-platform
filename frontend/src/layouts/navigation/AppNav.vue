<script setup lang="ts">
import { computed, nextTick, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resolveAppIcon, resolveMenuPresentation } from '@/app/registry/module-manifest'
import AppNavItem from './AppNavItem.vue'
import { useAuthStore } from '@/stores/auth'
import { isAllowedMenuPath } from '@/router/route-access'
import type { MenuItem } from '@/types/auth-models'
import { menuContextKey, type NavLink } from './menu-context'

interface Props {
  collapse?: boolean
  mode?: 'vertical' | 'horizontal'
  rounded?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  collapse: false,
  mode: 'vertical',
  rounded: false,
})

const emit = defineEmits<{
  select: [path: string, mode?: string]
  open: [id: string, parentsPath: string[]]
}>()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const menuRef = ref<HTMLElement | null>(null)

const activePath = computed(() => route.path)

const visibleLinks = computed(() => {
  return buildLinks(authStore.snapshot?.menus ?? [])
})

const topLevelLinks = computed(() => {
  return visibleLinks.value.filter((item) => item.to || item.children.length > 0)
})

const defaultOpeneds = computed(() => {
  const openeds = new Set<string>()
  const walk = (items: NavLink[]) => {
    for (const item of items) {
      if (item.children.some((child) => isPathActive(child, activePath.value))) {
        openeds.add(item.id)
      }
      walk(item.children)
    }
  }
  walk(visibleLinks.value)
  return openeds
})

const openedMenus = ref<Set<string>>(new Set())

watch(
  defaultOpeneds,
  (newOpeneds) => {
    if (props.collapse) {
      openedMenus.value = new Set()
      return
    }
    openedMenus.value = new Set([...openedMenus.value, ...newOpeneds])
  },
  { immediate: true },
)

watch(
  () => props.collapse,
  (collapse) => {
    if (collapse) {
      openedMenus.value = new Set()
    } else {
      openedMenus.value = new Set([...defaultOpeneds.value])
    }
  },
)

watch(
  () => props.collapse,
  (collapse) => {
    nextTick(() => {
      if (!collapse) {
        scrollActiveIntoView()
      }
    })
  },
)

function isPathActive(item: NavLink, path: string): boolean {
  if (item.to === path) return true
  return item.children.some((c) => isPathActive(c, path))
}

function toggleMenu(id: string) {
  const next = new Set(openedMenus.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    const parent = findParent(visibleLinks.value, id)
    if (parent) {
      parent.children.forEach((child) => {
        if (child.id !== id) next.delete(child.id)
      })
    } else {
      visibleLinks.value.forEach((item) => {
        if (item.id !== id) next.delete(item.id)
      })
    }
    next.add(id)
  }
  openedMenus.value = next
}

function setMenuOpen(id: string, open: boolean) {
  const next = new Set(openedMenus.value)
  if (!open) {
    next.delete(id)
    openedMenus.value = next
    return
  }

  const parent = findParent(visibleLinks.value, id)
  if (parent) {
    parent.children.forEach((child) => {
      if (child.id !== id) next.delete(child.id)
    })
  } else {
    visibleLinks.value.forEach((item) => {
      if (item.id !== id) next.delete(item.id)
    })
  }
  next.add(id)
  openedMenus.value = next
}

function findParent(items: NavLink[], id: string): NavLink | null {
  for (const item of items) {
    if (item.children.some((c) => c.id === id)) return item
    const found = findParent(item.children, id)
    if (found) return found
  }
  return null
}

function selectMenu(path: string) {
  if (path && route.path !== path) {
    void router.push(path)
  }
}

function handleSelect(key: string) {
  emit('select', key, props.mode)
  selectMenu(key)
}

function handleOpen(id: string, parentsPath: string[]) {
  emit('open', id, parentsPath)
}

function buildLinks(nodes: MenuItem[]): NavLink[] {
  const links: NavLink[] = []
  const usedPaths = new Set<string>()
  const snapshot = authStore.snapshot

  for (const node of nodes) {
    const rawPath = normalizeRoutePath(node.path)
    const path = rawPath && isAllowedMenuPath(snapshot, node) ? rawPath : ''
    const children = buildLinks(node.children ?? [])
    const fallbackPath = children[0]?.to ?? ''
    if (!path && !fallbackPath) {
      continue
    }
    const to = path || fallbackPath
    if (path && usedPaths.has(path)) {
      continue
    }
    if (path) {
      usedPaths.add(path)
    }
    const presentation = resolveMenuPresentation({
      code: node.code,
      routeKey: node.component ?? node.permission ?? node.code,
      title: node.name ?? node.title,
      icon: node.icon,
    })
    links.push({
      id: path || `menu-${node.id}`,
      to,
      label: presentation.title,
      icon: resolveAppIcon(presentation.icon),
      children,
    })
  }
  return links
}

function normalizeRoutePath(path?: string | null) {
  const normalized = path?.trim()
  if (!normalized) {
    return ''
  }
  return normalized.startsWith('/') ? normalized : `/${normalized}`
}

provide(menuContextKey, {
  get collapse() {
    return props.collapse
  },
  get activePath() {
    return activePath.value
  },
  get openedMenus() {
    return openedMenus.value
  },
  toggleMenu,
  setMenuOpen,
  selectMenu,
})

function scrollActiveIntoView() {
  nextTick(() => {
    const active = menuRef.value?.querySelector('.vben-menu-item.is-active, .vben-submenu-title.is-active')
    active?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  })
}

function handleScroll() {
  const menu = menuRef.value
  if (!menu) return
  isScrolled.value = menu.scrollTop > 4
}

const isScrolled = ref(false)

watch(activePath, scrollActiveIntoView, { immediate: true })
onMounted(() => {
  scrollActiveIntoView()
  const menu = menuRef.value
  if (menu) {
    menu.addEventListener('scroll', handleScroll, { passive: true })
  }
})
</script>

<template>
  <!-- 水平菜单模式 -->
  <nav
    v-if="mode === 'horizontal'"
    ref="menuRef"
    class="vben-menu vben-menu--horizontal"
    :class="[
      { 'is-scrolled': isScrolled },
      { 'is-rounded': rounded }
    ]"
  >
    <ul class="vben-menu-list vben-menu-list--horizontal">
      <AppNavItem
        v-for="item in topLevelLinks"
        :key="item.id"
        :item="item"
        :level="1"
        :mode="mode"
        @select="handleSelect"
        @toggle="toggleMenu"
      />
    </ul>
  </nav>

  <!-- 垂直菜单模式（默认） -->
  <nav
    v-else
    ref="menuRef"
    class="vben-menu"
    :class="{ 'is-collapse': collapse, 'is-scrolled': isScrolled }"
  >
    <ul class="vben-menu-list">
      <AppNavItem
        v-for="item in visibleLinks"
        :key="item.id"
        :item="item"
        @select="handleSelect"
        @toggle="toggleMenu"
      />
    </ul>
  </nav>
</template>

<style scoped lang="scss">
.vben-menu {
  --menu-item-height: 42px;
  --menu-item-radius: 10px;
  --menu-item-indent: 10px;
  --menu-item-icon-size: 16px;
  --menu-item-text-size: 14px;
  --menu-item-gap: 4px;
  --menu-submenu-padding: 10px;
  position: relative;

  &--horizontal {
    display: flex;
    align-items: center;
    height: 100%;
    overflow-x: auto;
    overflow-y: hidden;
    white-space: nowrap;
    padding: 0;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }
}

.vben-menu-list {
  display: flex;
  flex-direction: column;
  gap: var(--menu-item-gap);
  padding: 0 var(--menu-submenu-padding);
  margin: 0;
  list-style: none;

  &--horizontal {
    flex-direction: row;
    align-items: center;
    height: 100%;
    gap: 2px;
    padding: 0;
  }
}

.vben-menu-item,
.vben-submenu-title {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  height: var(--menu-item-height);
  padding: 0 13px 0 14px;
  border-radius: var(--menu-item-radius);
  font-size: var(--menu-item-text-size);
  font-weight: 500;
  color: hsl(var(--foreground) / 0.8);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
  white-space: nowrap;
  overflow: hidden;

  &:hover {
    color: hsl(var(--foreground));
    background-color: hsl(var(--accent));
  }

  &.is-active {
    color: hsl(var(--primary));
    background-color: hsl(var(--primary) / 0.075);
    font-weight: 600;
  }

  .vben-menu-icon {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: var(--menu-item-icon-size);
    height: var(--menu-item-icon-size);
    font-size: var(--menu-item-icon-size);
    transform: translateY(-0.5px);
  }

  .vben-menu-title {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .vben-menu-arrow {
    flex: 0 0 auto;
    font-size: 12px;
    color: hsl(var(--muted-foreground));
    transition: transform 0.2s ease;

    &.is-open {
      transform: rotate(180deg);
    }
  }
}

.vben-submenu {
  display: flex;
  flex-direction: column;
  gap: var(--menu-item-gap);
}

.vben-submenu-children {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.25s ease;

  &.is-open {
    grid-template-rows: 1fr;
  }

  > .vben-submenu-children-inner {
    overflow: hidden;
  }
}

.vben-submenu-children-inner > .vben-menu-list {
  padding-left: calc(var(--menu-item-indent) + 4px);
}

// 水平菜单模式样式
.vben-menu--horizontal {
  .vben-menu-list--horizontal {
    flex-direction: row;
  }

  .vben-submenu {
    flex-direction: row;
    position: relative;
  }

  .vben-submenu-children {
    position: absolute;
    top: 100%;
    left: 0;
    min-width: 180px;
    padding: 8px 0;
    border-radius: 10px;
    background-color: hsl(var(--popover));
    border: 1px solid hsl(var(--border));
    box-shadow: 0 10px 28px rgba(15, 23, 42, 0.12);
    grid-template-rows: 0fr;
    z-index: 1000;

    &.is-open {
      grid-template-rows: 1fr;
    }

    > .vben-submenu-children-inner {
      overflow: visible;
    }
  }

  .vben-submenu-children-inner > .vben-menu-list {
    padding-left: 0;
    gap: 2px;
  }

  .vben-menu-item,
  .vben-submenu-title {
    width: auto;
    border-radius: 6px;
    min-width: 0;
    padding: 0 12px;
    height: 32px;

    &:hover {
      background-color: hsl(var(--accent) / 0.9);
    }

    &.is-active {
      background-color: hsl(var(--primary) / 0.1);
    }
  }

  .vben-submenu-title {
    .vben-menu-arrow {
      margin-left: 2px;
    }
  }
}

// 圆角风格
.vben-menu.is-rounded {
  .vben-menu-item,
  .vben-submenu-title {
    border-radius: 999px;
  }
}

// 折叠状态
.vben-menu.is-collapse {
  padding: 8px 0 10px;

  > .vben-menu-list {
    padding: 0 7px;
    align-items: center;
  }

  .vben-menu-item,
  .vben-submenu-title {
    justify-content: center;
    padding: 0;
    width: 42px;
    height: 42px;
    border-radius: 10px;

    .vben-menu-title,
    .vben-menu-arrow {
      display: none;
    }

    .vben-menu-icon {
      width: 18px;
      height: 18px;
      font-size: 18px;
    }
  }

  .vben-submenu-children {
    display: none;
  }
}

// 暗色主题
.dark .vben-menu {
  scrollbar-color: hsl(var(--muted-foreground) / 0.25) transparent;

  &::-webkit-scrollbar-thumb {
    background-color: hsl(var(--muted-foreground) / 0.25);
  }
}

// 折叠时 hover 弹出的浮层菜单
.vben-menu-popup {
  position: fixed;
  z-index: 1000;
  min-width: 180px;
  max-height: calc(100vh - 80px);
  overflow-y: auto;
  padding: 8px;
  border-radius: 12px;
  background-color: hsl(var(--popover));
  border: 1px solid hsl(var(--border));
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.12);
  list-style: none;
  margin: 0;

  .vben-menu-item,
  .vben-submenu-title {
    color: hsl(var(--popover-foreground) / 0.9);

    &:hover {
      color: hsl(var(--popover-foreground));
      background-color: hsl(var(--accent));
    }

    &.is-active {
      color: hsl(var(--primary));
      background-color: hsl(var(--primary) / 0.12);
    }
  }

  .vben-submenu-children-inner > .vben-menu-list {
    padding-left: var(--menu-item-indent);
  }
}

// 弹出层进入/离开动画
.vben-menu-popup-enter-active,
.vben-menu-popup-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.vben-menu-popup-enter-from,
.vben-menu-popup-leave-to {
  opacity: 0;
  transform: translateX(-6px);
}

// 侧边栏菜单滚动阴影（在滚动时显示顶部/底部阴影）
.vben-menu {
  position: relative;

  &::before,
  &::after {
    content: '';
    position: sticky;
    left: 0;
    right: 0;
    height: 16px;
    z-index: 1;
    pointer-events: none;
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  &::before {
    top: 0;
    background: linear-gradient(to bottom, hsl(var(--background)), transparent);
  }

  &::after {
    bottom: 0;
    background: linear-gradient(to top, hsl(var(--background)), transparent);
  }

  &.is-scrolled {
    &::before,
    &::after {
      opacity: 1;
    }
  }
}

// 子菜单展开时的箭头旋转
.vben-submenu-title .vben-menu-arrow {
  transition: transform 0.25s ease;
}

.vben-submenu-children.is-open + .vben-submenu-title .vben-menu-arrow,
.vben-submenu-title.is-open .vben-menu-arrow {
  transform: rotate(180deg);
}

// 激活态菜单项在暗色模式下的微调
.dark .vben-menu-item.is-active,
.dark .vben-submenu-title.is-active {
  background-color: hsl(var(--primary) / 0.18);
}
</style>
