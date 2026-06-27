import { computed, ref, watch } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'

export interface TabItem {
  path: string
  title: string
  icon?: string
  pinned?: boolean
  key?: string
}

const STORAGE_KEY = 'ea_visited_views_cache'

const cachedViews = sessionStorage.getItem(STORAGE_KEY)
const tabs = ref<TabItem[]>(cachedViews ? JSON.parse(cachedViews) : [])
const updateTime = ref(Date.now())

watch(
  tabs,
  () => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tabs.value))
  },
  { deep: true },
)

export function useTabbarStore() {
  const currentTabs = computed(() => tabs.value)

  function getTabByKey(key: string): TabItem | undefined {
    return tabs.value.find((t) => (t.key || t.path) === key)
  }

  function setAffixTabs(routes: Array<{ meta?: Record<string, any>; path?: string }>) {
    const affixPaths = new Set<string>()
    for (const route of routes) {
      if (route.meta?.affixTab && route.path) {
        affixPaths.add(route.path)
      }
    }
    for (const path of affixPaths) {
      const exist = tabs.value.find((t) => t.path === path)
      if (!exist) {
        const meta = routes.find((r) => r.path === path)?.meta
        tabs.value.push({
          path,
          title: String(meta?.title ?? '控制台'),
          icon: String(meta?.icon ?? ''),
          key: path,
          pinned: true,
        })
      }
    }
  }

  function addTab(route: RouteLocationNormalizedLoaded) {
    if (!route.path || route.name === 'login') return
    
    // 如果已存在该标签页，直接返回
    const exist = tabs.value.find((t) => t.path === route.path)
    if (exist) return
    
    // 检查标签页数量限制
    const maxCount = 20 // 默认值，应从 preferences 中读取
    if (tabs.value.length >= maxCount && !route.meta?.affixTab) {
      // 达到最大数量，移除最早的未固定标签页
      const unpinned = tabs.value.filter((t) => !t.pinned)
      if (unpinned.length > 0) {
        tabs.value = tabs.value.filter((t) => t.pinned || t.path === route.path)
      }
    }
    
    const meta = route.matched?.[route.matched.length - 1]?.meta || route.meta
    tabs.value.push({
      path: route.path,
      title: String(meta?.title ?? '控制台'),
      icon: String(meta?.icon ?? ''),
      key: route.fullPath || route.path,
      pinned: route.path === '/dashboard',
    })
    updateTime.value = Date.now()
  }

  function closeTab(path: string) {
    tabs.value = tabs.value.filter((t) => t.path !== path || t.pinned)
    updateTime.value = Date.now()
  }

  function closeOtherTabs(activePath: string) {
    tabs.value = tabs.value.filter((t) => t.pinned || t.path === activePath)
    updateTime.value = Date.now()
  }

  function closeAllTabs() {
    tabs.value = tabs.value.filter((t) => t.pinned)
    updateTime.value = Date.now()
  }

  function closeLeftTabs(activePath: string) {
    const index = tabs.value.findIndex((t) => t.path === activePath)
    if (index <= 0) return
    const pinned = tabs.value.filter((t) => t.pinned)
    const unpinned = tabs.value.slice(index)
    tabs.value = [...pinned, ...unpinDuplicates(unpinned)]
    updateTime.value = Date.now()
  }

  function closeRightTabs(activePath: string) {
    const index = tabs.value.findIndex((t) => t.path === activePath)
    if (index < 0) return
    const pinned = tabs.value.filter((t) => t.pinned)
    const unpinned = tabs.value.slice(0, index + 1)
    tabs.value = [...pinned, ...unpinDuplicates(unpinned)]
    updateTime.value = Date.now()
  }

  function closeTabByKey(key: string) {
    const tab = getTabByKey(key)
    if (!tab) return
    closeTab(tab.path)
  }

  function sortTabs(newTabs: TabItem[]) {
    const pinned = tabs.value.filter((t) => t.pinned)
    const unpinned = newTabs.filter((t) => !t.pinned)
    tabs.value = [...pinned, ...unpinDuplicates(unpinned)]
    updateTime.value = Date.now()
  }

  function pinTab(path: string) {
    const tab = tabs.value.find((t) => t.path === path)
    if (tab) {
      tab.pinned = true
      updateTime.value = Date.now()
    }
  }

  function unpinTab(path: string) {
    const tab = tabs.value.find((t) => t.path === path)
    if (tab && tab.path !== '/dashboard') {
      tab.pinned = false
      updateTime.value = Date.now()
    }
  }

  function resetTabs() {
    tabs.value = []
    updateTime.value = Date.now()
  }

  const getMenuList = computed(() => {
    const list: string[] = ['close', 'affix', 'maximize', 'reload', 'open-in-new-window']
    if (tabs.value.length > 1) {
      list.push('close-left', 'close-right', 'close-other', 'close-all')
    }
    return list
  })

  return {
    tabs: currentTabs,
    updateTime,
    getTabByKey,
    setAffixTabs,
    addTab,
    closeTab,
    closeOtherTabs,
    closeAllTabs,
    closeLeftTabs,
    closeRightTabs,
    closeTabByKey,
    sortTabs,
    pinTab,
    unpinTab,
    resetTabs,
    getMenuList,
  }
}

function unpinDuplicates(items: TabItem[]): TabItem[] {
  const seen = new Set<string>()
  return items.filter((item) => {
    if (seen.has(item.path)) {
      return false
    }
    seen.add(item.path)
    return true
  })
}