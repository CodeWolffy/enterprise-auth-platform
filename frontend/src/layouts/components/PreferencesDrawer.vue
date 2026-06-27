<template>
  <el-drawer
    v-model="visible"
    title="偏好设置"
    direction="rtl"
    size="400px"
    class="preferences-drawer"
  >
    <el-tabs v-model="activeTab" class="preferences-tabs">
      <el-tab-pane label="外观" name="appearance">
        <div class="preferences-body">
          <!-- 布局模式 -->
          <section class="preferences-section">
            <h4 class="preferences-section-title">布局模式</h4>
            <div class="preferences-grid">
              <div
                v-for="mode in layoutModes"
                :key="mode.value"
                class="preferences-card"
                :class="{ active: preferences.layout === mode.value }"
                @click="updatePreferences({ layout: mode.value })"
              >
                <div class="preferences-card-icon">
                  <el-icon :size="24"><component :is="mode.icon" /></el-icon>
                </div>
                <span class="preferences-card-label">{{ mode.label }}</span>
              </div>
            </div>
          </section>

          <!-- 主题模式 -->
          <section class="preferences-section">
            <h4 class="preferences-section-title">主题模式</h4>
            <el-radio-group :model-value="preferences.theme" @update:model-value="updatePreferences({ theme: $event as any })">
              <el-radio-button label="light">亮色</el-radio-button>
              <el-radio-button label="dark">暗色</el-radio-button>
              <el-radio-button label="auto">跟随系统</el-radio-button>
            </el-radio-group>
          </section>

          <!-- 圆角风格 -->
          <section class="preferences-section">
            <h4 class="preferences-section-title">界面风格</h4>
            <div class="preferences-row">
              <span class="preferences-label">大圆角</span>
              <el-switch :model-value="preferences.rounded" @update:model-value="updatePreferences({ rounded: $event as boolean })" />
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="布局" name="layout">
        <div class="preferences-body">
          <!-- 侧边栏 -->
          <section class="preferences-section">
            <h4 class="preferences-section-title">侧边栏</h4>
            <div class="preferences-row">
              <span class="preferences-label">默认折叠</span>
              <el-switch :model-value="preferences.sidebarCollapsed" @update:model-value="updatePreferences({ sidebarCollapsed: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">折叠显示标题</span>
              <el-switch :model-value="preferences.sidebarCollapseShowTitle" @update:model-value="updatePreferences({ sidebarCollapseShowTitle: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">Hover 展开</span>
              <el-switch :model-value="preferences.sidebarExpandOnHover" @update:model-value="updatePreferences({ sidebarExpandOnHover: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">侧边栏宽度</span>
              <el-input-number :model-value="preferences.sidebarWidth" @update:model-value="updatePreferences({ sidebarWidth: $event as number })" :min="160" :max="320" :step="10" size="small" />
            </div>
          </section>

          <!-- 标签页 -->
          <section class="preferences-section">
            <h4 class="preferences-section-title">标签页</h4>
            <div class="preferences-row">
              <span class="preferences-label">启用标签页</span>
              <el-switch :model-value="preferences.tabbarEnable" @update:model-value="updatePreferences({ tabbarEnable: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">显示图标</span>
              <el-switch :model-value="preferences.tabbarShowIcon" @update:model-value="updatePreferences({ tabbarShowIcon: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">可拖拽</span>
              <el-switch :model-value="preferences.tabbarDraggable" @update:model-value="updatePreferences({ tabbarDraggable: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">滚轮切换</span>
              <el-switch :model-value="preferences.tabbarWheelable" @update:model-value="updatePreferences({ tabbarWheelable: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">持久化</span>
              <el-switch :model-value="preferences.tabbarPersist" @update:model-value="updatePreferences({ tabbarPersist: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">中键关闭</span>
              <el-switch :model-value="preferences.tabbarMiddleClickToClose ?? true" @update:model-value="updatePreferences({ tabbarMiddleClickToClose: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">标签页风格</span>
              <el-select :model-value="preferences.tabbarStyleType" @update:model-value="updatePreferences({ tabbarStyleType: $event as string })" size="small">
                <el-option label="Chrome" value="chrome" />
                <el-option label="简约" value="plain" />
                <el-option label="卡片" value="card" />
                <el-option label="活力" value="brisk" />
              </el-select>
            </div>
            <div class="preferences-row">
              <span class="preferences-label">标签页最大数量</span>
              <el-input-number :model-value="preferences.tabbarMaxCount" @update:model-value="updatePreferences({ tabbarMaxCount: $event as number })" :min="5" :max="50" :step="1" size="small" />
            </div>
          </section>

          <!-- 头部 -->
          <section class="preferences-section">
            <h4 class="preferences-section-title">顶部栏</h4>
            <div class="preferences-row">
              <span class="preferences-label">固定头部</span>
              <el-switch :model-value="preferences.headerFixed" @update:model-value="updatePreferences({ headerFixed: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">显示面包屑</span>
              <el-switch :model-value="preferences.breadcrumbEnable" @update:model-value="updatePreferences({ breadcrumbEnable: $event as boolean })" />
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="快捷键" name="shortcutKey">
        <div class="preferences-body">
          <section class="preferences-section">
            <h4 class="preferences-section-title">全局快捷键</h4>
            <div class="preferences-row">
              <span class="preferences-label">启用快捷键</span>
              <el-switch :model-value="preferences.shortcutKeysEnable" @update:model-value="updatePreferences({ shortcutKeysEnable: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">全局搜索</span>
              <el-switch :model-value="preferences.shortcutKeysGlobalSearch" @update:model-value="updatePreferences({ shortcutKeysGlobalSearch: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">锁屏</span>
              <el-switch :model-value="preferences.shortcutKeysGlobalLockScreen" @update:model-value="updatePreferences({ shortcutKeysGlobalLockScreen: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">退出登录</span>
              <el-switch :model-value="preferences.shortcutKeysGlobalLogout" @update:model-value="updatePreferences({ shortcutKeysGlobalLogout: $event as boolean })" />
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="通用" name="general">
        <div class="preferences-body">
          <section class="preferences-section">
            <h4 class="preferences-section-title">通用设置</h4>
            <div class="preferences-row">
              <span class="preferences-label">动态标题</span>
              <el-switch :model-value="preferences.appDynamicTitle" @update:model-value="updatePreferences({ appDynamicTitle: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">检查更新</span>
              <el-switch :model-value="preferences.appEnableCheckUpdates" @update:model-value="updatePreferences({ appEnableCheckUpdates: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">水印</span>
              <el-switch :model-value="preferences.appWatermark" @update:model-value="updatePreferences({ appWatermark: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">语言</span>
              <el-select :model-value="preferences.appLocale" @update:model-value="updatePreferences({ appLocale: $event as string })" size="small">
                <el-option label="简体中文" value="zh-CN" />
                <el-option label="English" value="en-US" />
              </el-select>
            </div>
          </section>

          <section class="preferences-section">
            <h4 class="preferences-section-title">动画效果</h4>
            <div class="preferences-row">
              <span class="preferences-label">启用过渡动画</span>
              <el-switch :model-value="preferences.transitionEnable" @update:model-value="updatePreferences({ transitionEnable: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">加载动画</span>
              <el-switch :model-value="preferences.transitionLoading" @update:model-value="updatePreferences({ transitionLoading: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">过渡进度条</span>
              <el-switch :model-value="preferences.transitionProgress" @update:model-value="updatePreferences({ transitionProgress: $event as boolean })" />
            </div>
            <div class="preferences-row">
              <span class="preferences-label">动画风格</span>
              <el-select :model-value="preferences.transitionName" @update:model-value="updatePreferences({ transitionName: $event as string })" size="small">
                <el-option label="滑动渐显" value="fade-slide" />
                <el-option label="渐显" value="fade" />
                <el-option label="缩放" value="zoom" />
                <el-option label="无" value="none" />
              </el-select>
            </div>
          </section>
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <div class="preferences-footer">
        <el-button :disabled="!diffPreference" @click="handleCopy">复制偏好配置</el-button>
        <el-button :disabled="!diffPreference" type="primary" plain @click="handleReset">恢复默认</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  Fold,
  Grid,
  Monitor,
  Operation,
  Platform,
  Rank,
} from '@element-plus/icons-vue'
import { usePreferences, type LayoutMode } from '@/composables/usePreferences'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const { preferences, updatePreferences, resetPreferences, defaultPreferences } = usePreferences()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const activeTab = ref('appearance')

const originalPreferences = ref<Preferences>(JSON.parse(JSON.stringify(preferences)))

watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      originalPreferences.value = JSON.parse(JSON.stringify(preferences))
    }
  },
)

const diffPreference = computed(() => {
  return JSON.stringify(preferences) !== JSON.stringify(originalPreferences.value)
})

const layoutModes: { value: LayoutMode; label: string; icon: any }[] = [
  { value: 'sidebar-nav', label: '侧边栏', icon: Platform },
  { value: 'mixed-nav', label: '混合', icon: Grid },
  { value: 'header-nav', label: '顶部栏', icon: Monitor },
  { value: 'header-mixed-nav', label: '顶部混合', icon: Operation },
  { value: 'sidebar-mixed-nav', label: '侧边混合', icon: Rank },
  { value: 'full-content', label: '全内容', icon: Fold },
]

function handleReset() {
  resetPreferences()
}

async function handleCopy() {
  const config = JSON.stringify(preferences, null, 2)
  try {
    await navigator.clipboard.writeText(config)
    // TODO: 可接入 ElMessage 提示
  } catch {
    // ignore
  }
}
</script>

<style scoped lang="scss">
.preferences-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 60px;
}

.preferences-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preferences-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--card));
}

.preferences-section-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: hsl(var(--foreground));
}

.preferences-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.preferences-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 8px;
  border-radius: 10px;
  border: 1px solid hsl(var(--border));
  background: hsl(var(--background));
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: hsl(var(--primary) / 0.4);
  }

  &.active {
    border-color: hsl(var(--primary));
    background: hsl(var(--primary) / 0.08);
    color: hsl(var(--primary));
  }
}

.preferences-card-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.preferences-card-label {
  font-size: 12px;
  font-weight: 500;
}

.preferences-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.preferences-label {
  font-size: 13px;
  color: hsl(var(--foreground) / 0.85);
}

.preferences-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

:deep(.el-radio-group) {
  display: flex;
  width: 100%;
}

:deep(.el-radio-button) {
  flex: 1;

  .el-radio-button__inner {
    width: 100%;
  }
}
</style>