import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useTabbarStore, type TabItem } from '@/stores/tabbar'

export interface ContextMenuItem {
  key: string
  text: string
  icon?: any
  disabled?: boolean
  separator?: boolean
}

export function useTabbar() {
  const route = useRoute()
  const tabbarStore = useTabbarStore()

  // 保持响应式引用，供 LayoutTabbar 消费
  const currentTabs = ref<TabItem[]>(tabbarStore.tabs.value)
  watch(
    () => tabbarStore.tabs.value,
    (newTabs) => {
      currentTabs.value = newTabs
    },
    { immediate: true, deep: true },
  )

  // 路由变更时自动添加标签页（恢复自原 vben 逻辑）
  watch(
    () => route.fullPath,
    () => {
      if (!route.path || route.name === 'login') return
      tabbarStore.addTab(route)
    },
    { immediate: true },
  )

  const handleClose = (key: string) => {
    tabbarStore.closeTabByKey(key)
  }

  const createContextMenus = (tab: TabItem): ContextMenuItem[] => {
    const hasMultiple = tabbarStore.tabs.value.length > 1

    const menus: ContextMenuItem[] = [
      {
        key: 'close',
        text: '关闭',
        disabled: tab.pinned,
      },
      {
        key: 'pin',
        text: tab.pinned ? '取消固定' : '固定',
      },
      {
        key: 'refresh',
        text: '重新加载',
        separator: true,
      },
      ...(hasMultiple
        ? [
            {
              key: 'close-left',
              text: '关闭左侧',
            },
            {
              key: 'close-right',
              text: '关闭右侧',
              separator: true,
            },
            {
              key: 'closeOther',
              text: '关闭其他',
            },
            {
              key: 'closeAll',
              text: '关闭全部',
            },
          ]
        : []),
    ] as unknown as ContextMenuItem[]

    return menus
  }

  return {
    createContextMenus,
    currentTabs,
    handleClose,
  }
}