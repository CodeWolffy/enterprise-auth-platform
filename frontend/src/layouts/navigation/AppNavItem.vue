<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import CollapseTransition from './CollapseTransition.vue'
import { menuContextKey, type MenuContext, type NavLink } from './menu-context'

interface Props {
  item: NavLink
  level?: number
  forceExpand?: boolean
  mode?: 'vertical' | 'horizontal'
}

const props = withDefaults(defineProps<Props>(), {
  level: 1,
  forceExpand: false,
  mode: 'vertical',
})

const emit = defineEmits<{
  select: [path: string]
  toggle: [id: string]
}>()

const router = useRouter()
const context = inject<MenuContext>(menuContextKey)

const level = computed(() => props.level)
const isLeaf = computed(() => props.item.children.length === 0)
const collapse = computed(() => (props.forceExpand ? false : context?.collapse ?? false))
const activePath = computed(() => context?.activePath ?? '')
const openedMenus = computed(() => context?.openedMenus ?? new Set<string>())

const isCollapsedWithChildren = computed(() => collapse.value && !isLeaf.value)
const isCollapsedLeaf = computed(() => collapse.value && isLeaf.value)

const isActive = computed(() => {
  if (isLeaf.value) {
    return activePath.value === props.item.to
  }
  return (
    props.item.to === activePath.value ||
    props.item.children.some((c) => isPathActive(c, activePath.value))
  )
})

const isOpen = computed(() => openedMenus.value.has(props.item.id))
const isHorizontal = computed(() => props.mode === 'horizontal')
const openTimer = ref<number | null>(null)
const closeTimer = ref<number | null>(null)

function clearMenuTimers() {
  if (openTimer.value !== null) {
    window.clearTimeout(openTimer.value)
    openTimer.value = null
  }
  if (closeTimer.value !== null) {
    window.clearTimeout(closeTimer.value)
    closeTimer.value = null
  }
}

function isPathActive(item: NavLink, path: string): boolean {
  if (item.to === path) return true
  return item.children.some((c) => isPathActive(c, path))
}

function handleSelect() {
  if (!props.item.to) return
  if (isHorizontal.value) {
    emit('select', props.item.to)
  } else {
    context?.selectMenu(props.item.to)
  }
}

function handleToggle() {
  if (isHorizontal.value) {
    emit('toggle', props.item.id)
  } else {
    context?.toggleMenu(props.item.id)
  }
}

function handleMouseEnter() {
  if (!isLeaf.value && isHorizontal.value) {
    if (closeTimer.value !== null) {
      window.clearTimeout(closeTimer.value)
      closeTimer.value = null
    }
    openTimer.value = window.setTimeout(() => {
      context?.setMenuOpen?.(props.item.id, true)
      openTimer.value = null
    }, 40)
  }
}

function handleMouseLeave() {
  if (!isLeaf.value && isHorizontal.value) {
    if (openTimer.value !== null) {
      window.clearTimeout(openTimer.value)
      openTimer.value = null
    }
    closeTimer.value = window.setTimeout(() => {
      context?.setMenuOpen?.(props.item.id, false)
      closeTimer.value = null
    }, 160)
  }
}

function handleDropdownVisibleChange(visible: boolean) {
  clearMenuTimers()
  context?.setMenuOpen?.(props.item.id, visible)
}

function goToDefaultPath() {
  if (props.item.to) {
    void router.push(props.item.to)
  }
}

function handleSelectChild(child: NavLink) {
  const target = child.to || child.children[0]?.to
  if (!target) return
  emit('select', target)
  context?.selectMenu(target)
}

onBeforeUnmount(() => {
  clearMenuTimers()
})
</script>

<template>
  <!-- 水平菜单模式 -->
  <template v-if="isHorizontal">
    <!-- 叶子节点 -->
    <div
      v-if="isLeaf"
      class="vben-menu-item"
      :class="{ 'is-active': isActive }"
      @click="handleSelect"
    >
      <el-icon class="vben-menu-icon" v-if="item.icon">
        <component :is="item.icon" />
      </el-icon>
      <span class="vben-menu-title">{{ item.label }}</span>
    </div>

    <!-- 父节点 - 使用 el-dropdown -->
    <el-dropdown
      v-else
      trigger="hover"
      popper-class="header-menu-dropdown"
      :show-timeout="40"
      :hide-timeout="160"
      @visible-change="handleDropdownVisibleChange"
    >
      <div
        class="vben-submenu-title"
        :class="{ 'is-active': isActive, 'is-open': isOpen }"
        @mouseenter="handleMouseEnter"
        @mouseleave="handleMouseLeave"
      >
        <el-icon class="vben-menu-icon" v-if="item.icon">
          <component :is="item.icon" />
        </el-icon>
        <span class="vben-menu-title">{{ item.label }}</span>
        <el-icon class="vben-menu-arrow" :class="{ 'is-open': isOpen }">
          <ArrowDown />
        </el-icon>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="child in item.children"
            :key="child.id"
            @click="handleSelectChild(child)"
          >
            <div class="flex items-center gap-2">
              <el-icon v-if="child.icon" class="vben-menu-icon">
                <component :is="child.icon" />
              </el-icon>
              <span>{{ child.label }}</span>
            </div>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </template>

  <!-- 垂直菜单模式（默认） -->
  <template v-else>
    <li class="vben-submenu" :class="{ 'is-leaf': isLeaf }">
      <!-- 折叠态且有子菜单：hover 弹出浮层 -->
      <template v-if="isCollapsedWithChildren">
        <el-popover
          :offset="8"
          placement="right-start"
          :show-arrow="false"
          :width="200"
          trigger="hover"
          popper-class="vben-menu-popup"
          :teleported="true"
        >
          <template #reference>
            <div
              class="vben-submenu-title"
              :class="{ 'is-active': isActive }"
              @click="goToDefaultPath"
            >
              <el-icon class="vben-menu-icon">
                <component :is="item.icon" />
              </el-icon>
            </div>
          </template>
          <ul class="vben-menu-list">
            <AppNavItem
              v-for="child in item.children"
              :key="child.id"
              :item="child"
              :level="level + 1"
              force-expand
            />
          </ul>
        </el-popover>
      </template>

      <!-- 折叠态叶子节点：tooltip -->
      <template v-else-if="isCollapsedLeaf">
        <el-tooltip :content="item.label" placement="right" :offset="8">
          <div
            class="vben-menu-item"
            :class="{ 'is-active': isActive }"
            @click="handleSelect"
          >
            <el-icon class="vben-menu-icon">
              <component :is="item.icon" />
            </el-icon>
          </div>
        </el-tooltip>
      </template>

      <!-- 展开态叶子节点 -->
      <template v-else-if="isLeaf">
        <div
          class="vben-menu-item"
          :class="{ 'is-active': isActive }"
          @click="handleSelect"
        >
          <el-icon class="vben-menu-icon">
            <component :is="item.icon" />
          </el-icon>
          <span class="vben-menu-title">{{ item.label }}</span>
        </div>
      </template>

      <!-- 展开态父节点 -->
      <template v-else>
        <div
          class="vben-submenu-title"
          :class="{ 'is-active': isActive }"
          @click="handleToggle"
        >
          <el-icon class="vben-menu-icon">
            <component :is="item.icon" />
          </el-icon>
          <span class="vben-menu-title">{{ item.label }}</span>
          <el-icon class="vben-menu-arrow" :class="{ 'is-open': isOpen }">
            <ArrowDown />
          </el-icon>
        </div>
        <CollapseTransition>
          <ul
            v-show="isOpen"
            class="vben-menu-list vben-submenu-list"
          >
            <AppNavItem
              v-for="child in item.children"
              :key="child.id"
              :item="child"
              :level="level + 1"
            />
          </ul>
        </CollapseTransition>
      </template>
    </li>
  </template>
</template>

<style scoped lang="scss">
.vben-menu-item,
.vben-submenu-title {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: var(--menu-item-height);
  padding: 0 14px;
  border-radius: var(--menu-item-radius);
  font-size: var(--menu-item-text-size);
  font-weight: 500;
  color: hsl(var(--foreground) / 0.85);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
  white-space: nowrap;
  overflow: hidden;

  &:hover {
    color: hsl(var(--foreground));
    background-color: hsl(var(--accent) / 0.9);

    .vben-menu-icon {
      color: hsl(var(--primary));
      background: transparent;
    }
  }

  &.is-active {
    color: hsl(var(--primary));
    background-color: hsl(var(--primary) / 0.075);
    font-weight: 600;
    box-shadow: inset 0 0 0 1px hsl(var(--primary) / 0.04);

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 8px;
      bottom: 8px;
      width: 3px;
      border-radius: 999px;
      background: hsl(var(--primary));
    }

    .vben-menu-icon {
      color: hsl(var(--primary));
      background: transparent;
    }
  }

  .vben-menu-icon {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 16px;
    height: 16px;
    font-size: var(--menu-item-icon-size);
    border-radius: 0;
    transform: translateY(-0.5px);
    transition: background-color 0.2s ease, color 0.2s ease;
  }

  .vben-menu-title {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1;
    transform: translateY(0.5px);
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

.vben-submenu-list {
  overflow: hidden;
  padding-top: 2px;
  padding-bottom: 0;
  padding-left: 7px;
}

:deep(.collapse-transition-enter-active),
:deep(.collapse-transition-leave-active) {
  transition:
    max-height 0.22s cubic-bezier(0.2, 0, 0, 1),
    padding-top 0.22s cubic-bezier(0.2, 0, 0, 1),
    padding-bottom 0.22s cubic-bezier(0.2, 0, 0, 1),
    margin-top 0.22s cubic-bezier(0.2, 0, 0, 1),
    margin-bottom 0.22s cubic-bezier(0.2, 0, 0, 1);
}

// 水平菜单模式样式
.vben-menu--horizontal {
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

  .vben-menu-list {
    flex-direction: row;
    align-items: center;
    height: 100%;
    gap: 4px;
    padding: 0;
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
    border-radius: 8px;
    transition: background-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;

    &:hover,
    &.is-open {
      background-color: hsl(var(--accent));
      box-shadow: inset 0 0 0 1px hsl(var(--primary) / 0.04);
    }

    &.is-active {
      background-color: hsl(var(--primary) / 0.12);
    }
  }

  .vben-submenu-title {
    .vben-menu-arrow {
      margin-left: 2px;
    }
  }
}

:deep(.header-menu-dropdown) {
  padding: 6px;
  border-radius: 12px;
  border: 1px solid hsl(var(--border));
  box-shadow: 0 12px 30px rgb(15 23 42 / 0.12);
  backdrop-filter: blur(12px);
}

:deep(.header-menu-dropdown .el-dropdown-menu__item) {
  min-width: 156px;
  height: 32px;
  border-radius: 8px;
  color: hsl(var(--foreground));
  transition: background-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

:deep(.header-menu-dropdown .el-dropdown-menu__item:hover) {
  background: hsl(var(--accent));
  color: hsl(var(--primary));
  transform: translateX(1px);
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
  padding: 8px 0;

  > .vben-menu-list {
    padding: 0 8px;
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
    padding-left: 8px;
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
