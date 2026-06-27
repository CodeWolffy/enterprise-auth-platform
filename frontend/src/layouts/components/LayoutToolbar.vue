<template>
  <div class="layout-toolbar">
    <div v-if="canSwitchTenant" class="layout-toolbar__tenant hidden xl:flex">
      <span class="layout-toolbar__label">{{ tenantLabel }}</span>
      <el-select
        :model-value="currentTenantId"
        placeholder="切换租户"
        size="small"
        :loading="tenantLoading"
        :disabled="tenantLoading"
        class="toolbar-tenant-select"
        :style="{ width: tenantSelectWidth }"
        @change="emit('tenant-change', $event)"
      >
        <el-option
          v-for="tenant in displayTenantOptions"
          :key="tenant.tenantId"
          :label="tenantOptionLabel(tenant)"
          :value="tenant.tenantId"
        />
      </el-select>
    </div>

    <button type="button" class="search-capsule hidden xl:flex" @click="emit('open-search')">
      <el-icon><Search /></el-icon>
      <span class="search-text">全局搜索</span>
      <kbd class="search-shortcut">Ctrl K</kbd>
    </button>

    <div class="layout-toolbar__divider hidden lg:block" />

    <slot name="notification" />

    <el-tooltip content="在线设备管理" placement="bottom">
      <button type="button" class="header-icon-btn" data-testid="header-online-devices" @click="emit('open-sessions')">
        <el-icon><Monitor /></el-icon>
      </button>
    </el-tooltip>

    <el-tooltip content="偏好设置" placement="bottom">
      <button type="button" class="header-icon-btn" @click="emit('open-preferences')">
        <el-icon><Setting /></el-icon>
      </button>
    </el-tooltip>

    <el-tooltip :content="isDark ? '亮色模式' : '暗色模式'" placement="bottom">
      <button type="button" class="header-icon-btn" @click="emit('toggle-theme')">
        <el-icon>
          <Sunny v-if="isDark" />
          <Moon v-else />
        </el-icon>
      </button>
    </el-tooltip>

    <el-tooltip content="语言" placement="bottom">
      <button type="button" class="header-icon-btn" @click="emit('toggle-language')">
        <el-icon>
          <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg" width="1em" height="1em">
            <path fill="currentColor" d="M140 188h584v164h-76v-88H216v484h356v76H140z"/>
            <path fill="currentColor" d="M400 340h-76C324 233.9 410 148 516 148c96 0 175.5 70.8 190.2 163.3l-75.1 11.3A116.2 116.2 0 0 0 516 224c-64.2 0-116 52-116 116m484 536H516V512h368z"/>
          </svg>
        </el-icon>
      </button>
    </el-tooltip>

    <el-tooltip content="全屏" placement="bottom">
      <button type="button" class="header-icon-btn" @click="emit('toggle-fullscreen')">
        <el-icon><FullScreen /></el-icon>
      </button>
    </el-tooltip>

    <el-dropdown
      trigger="click"
      popper-class="layout-toolbar__dropdown"
      @visible-change="handleUserMenuVisibleChange"
      @command="emit('user-command', $event)"
    >
      <div class="layout-toolbar__user" :class="{ 'is-open': userMenuOpen }" data-testid="user-menu-button">
        <div class="layout-toolbar__identity hidden lg:flex">
          <strong>{{ userName || '企业用户' }}</strong>
          <span>{{ canSwitchTenant ? '可切换租户' : '当前会话' }}</span>
        </div>
        <div class="relative">
          <el-avatar :size="28" :src="userAvatarUrl || undefined" class="bg-primary text-primary-foreground text-sm font-semibold">
            {{ avatarName }}
          </el-avatar>
          <span class="layout-toolbar__user-status" />
        </div>
        <el-icon class="layout-toolbar__user-arrow"><ArrowDown /></el-icon>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <div class="layout-toolbar__dropdown-user">
            <el-avatar :size="34" :src="userAvatarUrl || undefined" class="bg-primary text-primary-foreground text-sm font-semibold">
              {{ avatarName }}
            </el-avatar>
            <div class="layout-toolbar__dropdown-copy">
              <strong>{{ userName || '企业用户' }}</strong>
              <span>{{ canSwitchTenant ? '可切换租户' : '当前会话' }}</span>
              <em class="layout-toolbar__dropdown-meta">{{ canSwitchTenant ? '多租户工作台' : '单会话工作台' }}</em>
            </div>
          </div>
          <div class="layout-toolbar__dropdown-actions">
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item command="logout" data-testid="logout-button" divided>
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </div>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  ArrowDown,
  FullScreen,
  Monitor,
  Moon,
  Search,
  Setting,
  Sunny,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'

interface TenantOption {
  tenantId: string
  name: string
}

const props = defineProps<{
  tenantOptions?: TenantOption[]
  currentTenantId?: string
  operatorTenantId?: string
  tenantLoading?: boolean
  canSwitchTenant?: boolean
  userAvatarUrl?: string | null
  userName?: string | null
  isDark?: boolean
}>()

const emit = defineEmits<{
  'tenant-change': [tenantId: string]
  'open-search': []
  'open-sessions': []
  'open-preferences': []
  'toggle-theme': []
  'toggle-language': []
  'toggle-fullscreen': []
  'user-command': [command: string]
}>()

const avatarName = computed(() => {
  const name = props.userName || 'U'
  return name.charAt(0).toUpperCase()
})
const userMenuOpen = ref(false)

function handleUserMenuVisibleChange(visible: boolean) {
  userMenuOpen.value = visible
}

const platformTenantId = 'platform'
const canSwitchTenant = computed(() => Boolean(props.operatorTenantId) && props.operatorTenantId === platformTenantId)
const tenantLabel = computed(() => {
  if (props.operatorTenantId && props.currentTenantId && props.operatorTenantId !== props.currentTenantId) {
    return '当前租户'
  }
  return '登录租户'
})

function normalizeTenantOptions(options: TenantOption[] | undefined, currentTenantId?: string, operatorTenantId?: string) {
  const items = [...(options ?? [])]
  const hasOperatorTenant = Boolean(operatorTenantId) && items.some((tenant) => tenant.tenantId === operatorTenantId)
  if (operatorTenantId && !hasOperatorTenant) {
    items.unshift({ tenantId: operatorTenantId, name: operatorTenantId === platformTenantId ? '全平台视图' : '登录租户' })
  }
  if (currentTenantId && !items.some((tenant) => tenant.tenantId === currentTenantId)) {
    items.unshift({ tenantId: currentTenantId, name: '当前租户' })
  }
  return items
}

function tenantOptionLabel(tenant: TenantOption) {
  const suffixes: string[] = []
  if (tenant.tenantId === props.currentTenantId) {
    suffixes.push('当前')
  }
  if (tenant.tenantId === props.operatorTenantId && tenant.tenantId !== props.currentTenantId) {
    suffixes.push(tenant.tenantId === platformTenantId ? '全平台' : '返回平台')
  }
  const suffix = suffixes.length ? ` · ${suffixes.join(' / ')}` : ''
  return `${tenant.name} (${tenant.tenantId})${suffix}`
}

const displayTenantOptions = computed(() => normalizeTenantOptions(props.tenantOptions, props.currentTenantId, props.operatorTenantId))

const tenantSelectWidth = computed(() => {
  const longestOptionLength = displayTenantOptions.value.reduce((longest, tenant) => {
    return Math.max(longest, tenantOptionLabel(tenant).length)
  }, 0)
  return `${Math.min(Math.max(longestOptionLength * 8 + 44, 140), 300)}px`
})
</script>

<style scoped lang="scss">
.layout-toolbar {
  display: flex;
  height: 100%;
  align-items: center;
  gap: 3px;
  padding-right: 6px;
}

.layout-toolbar__tenant {
  align-items: center;
  gap: 8px;
  margin-right: 6px;
  padding-right: 8px;
}

.layout-toolbar__label {
  color: hsl(var(--muted-foreground));
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.layout-toolbar__divider {
  width: 1px;
  height: 18px;
  margin: 0 5px 0 3px;
  background: hsl(var(--border));
}

.header-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  font-size: 15px;
  color: hsl(var(--foreground) / 0.85);
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;

  &:hover {
    color: hsl(var(--primary));
    background-color: hsl(var(--accent));
  }
}

.layout-toolbar__user {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 38px;
  margin-left: 4px;
  padding: 0 8px 0 12px;
  border-radius: 18px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover,
  &.is-open {
    background: hsl(var(--accent));
    border-color: hsl(var(--primary) / 0.08);
    box-shadow: inset 0 0 0 1px hsl(var(--primary) / 0.05);
  }
}

.layout-toolbar__identity {
  display: grid;
  gap: 2px;
  text-align: right;

  strong {
    max-width: 112px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: hsl(var(--foreground));
    font-size: 12px;
    font-weight: 700;
  }

  span {
    color: hsl(var(--muted-foreground));
    font-size: 10px;
    line-height: 1;
  }
}

.layout-toolbar__user-status {
  position: absolute;
  right: -2px;
  top: -1px;
  width: 9px;
  height: 9px;
  border: 2px solid hsl(var(--header));
  border-radius: 999px;
  background: hsl(var(--success));
}

.layout-toolbar__user-arrow {
  color: hsl(var(--muted-foreground));
  font-size: 10px;
}

:deep(.layout-toolbar__dropdown) {
  padding: 6px;
  border-radius: 12px;
  border: 1px solid hsl(var(--border));
  box-shadow: 0 12px 30px rgb(15 23 42 / 0.12);
  backdrop-filter: blur(12px);
}

:deep(.layout-toolbar__dropdown .el-dropdown-menu__item) {
  gap: 8px;
  min-width: 156px;
  height: 32px;
  border-radius: 8px;
  color: hsl(var(--foreground));
}

.layout-toolbar__dropdown-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 10px;
  margin-bottom: 4px;
  border-bottom: 1px solid hsl(var(--border));
}

.layout-toolbar__dropdown-actions {
  display: grid;
  gap: 2px;
}

.layout-toolbar__dropdown-copy {
  display: grid;
  gap: 3px;
  min-width: 0;

  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 12px;
    color: hsl(var(--foreground));
  }

  span {
    font-size: 10px;
    color: hsl(var(--muted-foreground));
  }

  .layout-toolbar__dropdown-meta {
    display: inline-flex;
    align-items: center;
    width: fit-content;
    margin-top: 2px;
    padding: 2px 6px;
    border-radius: 999px;
    background: hsl(var(--accent));
    color: hsl(var(--foreground) / 0.76);
    font-size: 10px;
    font-style: normal;
    line-height: 1.2;
  }
}

.toolbar-tenant-select {
  :deep(.el-input__wrapper) {
    min-height: 34px;
    padding: 0 9px;
    border-radius: 18px;
    background-color: hsl(var(--background)) !important;
    box-shadow: 0 0 0 1px hsl(var(--border)) inset !important;
  }

  :deep(.el-input__wrapper:hover) {
    box-shadow: 0 0 0 1px hsl(var(--primary) / 0.18) inset !important;
  }

  :deep(.el-select__caret) {
    color: hsl(var(--muted-foreground));
    box-shadow: none !important;
  }

  :deep(.el-input__inner) {
    color: hsl(var(--foreground));
    font-weight: 600;
  }
}

.search-capsule {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 38px;
  min-width: 178px;
  padding: 0 12px;
  border: 1px solid hsl(var(--border));
  border-radius: 18px;
  background-color: hsl(var(--background));
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease;

  .el-icon {
    font-size: 14px;
    color: hsl(var(--muted-foreground));
  }

  &:hover {
    border-color: hsl(var(--primary) / 0.2);
    background-color: hsl(var(--accent));
  }
}

.search-text {
  font-size: 13px;
  min-width: 52px;
}

.search-shortcut {
  display: inline-flex;
  align-items: center;
  height: 20px;
  margin-left: auto;
  padding: 0 6px;
  border: 0;
  border-radius: 999px;
  background: hsl(var(--background));
  box-shadow: 0 0 0 1px hsl(var(--border)) inset;
  color: hsl(var(--muted-foreground));
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
}

.bg-primary {
  background: linear-gradient(135deg, #1677ff 0%, #0ea5e9 55%, #16c784 100%);
}
</style>
