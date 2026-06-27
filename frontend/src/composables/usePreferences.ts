import { computed, reactive, watch } from 'vue'

export type LayoutMode =
  | 'sidebar-nav'
  | 'mixed-nav'
  | 'header-nav'
  | 'header-mixed-nav'
  | 'sidebar-mixed-nav'
  | 'full-content'

export interface Preferences {
  layout: LayoutMode
  theme: 'light' | 'dark' | 'auto'
  rounded: boolean
  sidebarCollapsed: boolean
  sidebarCollapseShowTitle: boolean
  sidebarExpandOnHover: boolean
  sidebarWidth: number
  sidebarCollapseWidth: number
  tabbarEnable: boolean
  tabbarShowIcon: boolean
  tabbarDraggable: boolean
  tabbarWheelable: boolean
  tabbarPersist: boolean
  tabbarShowMore: boolean
  tabbarShowMaximize: boolean
  tabbarStyleType: 'chrome' | 'plain' | 'card' | 'brisk'
  tabbarMaxCount: number
  tabbarMiddleClickToClose: boolean
  tabbarMiddleClickToClose: boolean
  headerFixed: boolean
  headerMode: 'fixed' | 'auto' | 'auto-scroll' | 'static'
  headerHeight: number
  breadcrumbEnable: boolean
  breadcrumbShowIcon: boolean
  contentCompact: 'wide' | 'compact' | 'full'
  shortcutKeysEnable: boolean
  shortcutKeysGlobalSearch: boolean
  shortcutKeysGlobalLogout: boolean
  shortcutKeysGlobalLockScreen: boolean
  transitionEnable: boolean
  transitionLoading: boolean
  transitionName: string
  transitionProgress: boolean
}

const STORAGE_KEY = 'eap_preferences'
const SHELL_PRESET_VERSION = 3

const defaultPreferences: Preferences = {
  layout: 'sidebar-nav',
  theme: 'light',
  rounded: false,
  sidebarCollapsed: false,
  sidebarCollapseShowTitle: false,
  sidebarExpandOnHover: false,
  sidebarWidth: 252,
  sidebarCollapseWidth: 56,
  tabbarEnable: true,
  tabbarShowIcon: true,
  tabbarDraggable: true,
  tabbarWheelable: true,
  tabbarPersist: true,
  tabbarShowMore: true,
  tabbarShowMaximize: true,
  tabbarStyleType: 'chrome',
  tabbarMaxCount: 20,
  tabbarMiddleClickToClose: true,
  headerFixed: true,
  headerMode: 'fixed',
  headerHeight: 56,
  breadcrumbEnable: true,
  breadcrumbShowIcon: true,
  contentCompact: 'wide',
  shortcutKeysEnable: true,
  shortcutKeysGlobalSearch: true,
  shortcutKeysGlobalLogout: true,
  shortcutKeysGlobalLockScreen: true,
  transitionEnable: true,
  transitionLoading: true,
  transitionName: 'fade-slide',
  transitionProgress: true,
}

function loadPreferences(): Preferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const stored = JSON.parse(raw)
      if ((stored?.shellPresetVersion ?? 0) < SHELL_PRESET_VERSION) {
        return {
          ...defaultPreferences,
          ...stored,
          rounded: false,
          layout: 'sidebar-nav',
          sidebarCollapsed: false,
          sidebarExpandOnHover: false,
          sidebarWidth: 252,
          sidebarCollapseWidth: 56,
          tabbarStyleType: 'chrome',
          headerHeight: 56,
          shellPresetVersion: SHELL_PRESET_VERSION,
        } as Preferences
      }
      return { ...defaultPreferences, ...stored }
    }
  } catch {
    // ignore
  }
  return { ...defaultPreferences }
}

const preferences = reactive<Preferences>(loadPreferences())

watch(
  () => preferences,
  () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences))
  },
  { deep: true },
)

export function usePreferences() {
  const isSidebarNav = computed(() => preferences.layout === 'sidebar-nav')
  const isMixedNav = computed(() => preferences.layout === 'mixed-nav')
  const isHeaderNav = computed(() => preferences.layout === 'header-nav')
  const isHeaderMixedNav = computed(() => preferences.layout === 'header-mixed-nav')
  const isSidebarMixedNav = computed(() => preferences.layout === 'sidebar-mixed-nav')
  const isFullContent = computed(() => preferences.layout === 'full-content')
  const isSideMode = computed(
    () =>
      isSidebarNav.value ||
      isMixedNav.value ||
      isSidebarMixedNav.value ||
      isHeaderMixedNav.value,
  )

  function updatePreferences(partial: Partial<Preferences>) {
    Object.assign(preferences, partial)
  }

  function resetPreferences() {
    Object.assign(preferences, defaultPreferences)
  }

  return {
    defaultPreferences,
    preferences,
    isSidebarNav,
    isMixedNav,
    isHeaderNav,
    isHeaderMixedNav,
    isSidebarMixedNav,
    isFullContent,
    isSideMode,
    resetPreferences,
    updatePreferences,
  }
}
