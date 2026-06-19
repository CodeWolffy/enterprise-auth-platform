import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  buildNotificationStreamUrl,
  clearReadNotifications,
  markAllNotificationsRead,
  markNotificationRead as markNotificationReadRequest,
  queryNotifications,
  queryUnreadNotificationCount,
  type NotificationView,
} from '@/api/modules'

export function useNotifications() {
  const router = useRouter()
  const authStore = useAuthStore()
  const notificationsVisible = ref(false)
  const notificationsLoading = ref(false)
  const unreadNotificationCount = ref(0)
  const notificationReadFilter = ref<'all' | 'unread'>('all')
  const notificationPage = ref<{ total: number; page: number; size: number; records: NotificationView[] }>({
    total: 0,
    page: 1,
    size: 8,
    records: [],
  })
  const notificationSummaryText = computed(() => {
    if (notificationReadFilter.value === 'unread') {
      return `未读 ${notificationPage.value.total} 条 / 全部未读 ${unreadNotificationCount.value} 条`
    }
    return `共 ${notificationPage.value.total} 条通知，未读 ${unreadNotificationCount.value} 条`
  })
  // 当前列表中已读通知数量，用于判断"清空已读"按钮是否可用。
  const readNotificationCount = computed(() =>
    Math.max(notificationPage.value.total - unreadNotificationCount.value, 0)
  )

  let sseSource: EventSource | null = null
  let sseReconnectTimer: ReturnType<typeof setTimeout> | null = null
  let sseManualClose = false

  async function loadUnreadNotificationCount() {
    try {
      unreadNotificationCount.value = await queryUnreadNotificationCount()
    } catch {
      unreadNotificationCount.value = 0
    }
  }

  async function openNotifications() {
    notificationsVisible.value = true
    notificationPage.value.page = 1
    await loadNotifications()
  }

  async function loadNotifications(page = notificationPage.value.page) {
    notificationsLoading.value = true
    try {
      notificationPage.value = await queryNotifications({
        page,
        size: notificationPage.value.size,
        read: notificationReadFilter.value === 'unread' ? false : undefined,
      })
      await loadUnreadNotificationCount()
    } finally {
      notificationsLoading.value = false
    }
  }

  async function changeNotificationReadFilter(filter: 'all' | 'unread') {
    notificationReadFilter.value = filter
    notificationPage.value.page = 1
    await loadNotifications(1)
  }

  async function handleNotificationPageChange(page: number) {
    notificationPage.value.page = page
    await loadNotifications(page)
  }

  async function markNotificationRead(notification: NotificationView) {
    if (notification.read) {
      return
    }
    const updated = await markNotificationReadRequest(notification.id)
    if (notificationReadFilter.value === 'unread') {
      notificationPage.value.records = notificationPage.value.records.filter((item) => item.id !== updated.id)
      notificationPage.value.total = Math.max(notificationPage.value.total - 1, 0)
    } else {
      notificationPage.value.records = notificationPage.value.records.map((item) => item.id === updated.id ? updated : item)
    }
    await loadUnreadNotificationCount()
  }

  async function markAllNotificationsReadAction() {
    const changed = await markAllNotificationsRead()
    if (changed > 0) {
      ElMessage.success('站内通知已全部标记为已读')
    }
    notificationPage.value.page = 1
    await loadNotifications(1)
  }

  async function clearReadNotificationsAction() {
    try {
      await ElMessageBox.confirm(
        '确定要彻底删除所有已读通知吗？删除后将无法恢复。',
        '清空已读通知',
        {
          confirmButtonText: '彻底删除',
          cancelButtonText: '取消',
          type: 'warning',
          confirmButtonClass: 'el-button--danger',
        }
      )
    } catch {
      // 用户取消
      return
    }
    const removed = await clearReadNotifications()
    if (removed > 0) {
      ElMessage.success(`已清空 ${removed} 条已读通知`)
    } else {
      ElMessage.info('没有可清空的已读通知')
    }
    notificationPage.value.page = 1
    await loadNotifications(1)
  }

  async function openNotificationLink(notification: NotificationView) {
    await markNotificationRead(notification)
    if (!notification.link) {
      return
    }
    notificationsVisible.value = false
    await router.push(notification.link)
  }

  function handleWindowFocus() {
    void loadUnreadNotificationCount()
  }

  function startSseSubscription() {
    closeSseSubscription()
    const token = authStore.token
    if (!token) {
      return
    }
    sseManualClose = false
    try {
      sseSource = new EventSource(buildNotificationStreamUrl(token))
    } catch {
      scheduleSseReconnect()
      return
    }
    sseSource.addEventListener('open', () => {
      // 连接建立成功，立即同步一次未读数，避免遗漏订阅前的通知。
      void loadUnreadNotificationCount()
    })
    sseSource.addEventListener('notification', (event) => {
      try {
        const notification = JSON.parse((event as MessageEvent).data) as NotificationView
        handleIncomingNotification(notification)
      } catch {
        // 忽略解析失败的事件，保持连接稳定。
      }
    })
    sseSource.addEventListener('error', () => {
      // EventSource 在 error 后会自动尝试重连；这里兜底清理并触发一次延迟重连，
      // 防止部分浏览器在鉴权失效后不再自动恢复。
      closeSseSourceOnly()
      if (!sseManualClose) {
        scheduleSseReconnect()
      }
    })
  }

  function closeSseSourceOnly() {
    if (sseSource) {
      sseSource.close()
      sseSource = null
    }
  }

  function closeSseSubscription() {
    sseManualClose = true
    if (sseReconnectTimer) {
      clearTimeout(sseReconnectTimer)
      sseReconnectTimer = null
    }
    closeSseSourceOnly()
  }

  function scheduleSseReconnect() {
    if (sseReconnectTimer || sseManualClose) {
      return
    }
    sseReconnectTimer = setTimeout(() => {
      sseReconnectTimer = null
      if (!sseManualClose && authStore.token) {
        startSseSubscription()
      }
    }, 5000)
  }

  function handleIncomingNotification(notification: NotificationView) {
    unreadNotificationCount.value += 1
    // 若通知抽屉已打开且当前不是未读筛选，则把新通知插入到列表顶部，提供即时反馈。
    if (notificationsVisible.value && notificationReadFilter.value === 'all') {
      const exists = notificationPage.value.records.some((item) => item.id === notification.id)
      if (!exists) {
        notificationPage.value.records = [notification, ...notificationPage.value.records].slice(0, notificationPage.value.size)
        notificationPage.value.total += 1
      }
    }
    showNotificationToast(notification)
  }

  function showNotificationToast(notification: NotificationView) {
    const level = notification.level
    const type: 'success' | 'warning' | 'error' | 'info' =
      level === 'SUCCESS' ? 'success' :
      level === 'WARNING' ? 'warning' :
      level === 'ERROR' ? 'error' : 'info'
    ElMessage({
      type,
      message: notification.title,
      duration: 4500,
      grouping: true,
      offset: 24,
    })
  }

  onMounted(() => {
    window.addEventListener('focus', handleWindowFocus)
    startSseSubscription()
  })

  onBeforeUnmount(() => {
    window.removeEventListener('focus', handleWindowFocus)
    closeSseSubscription()
  })

  return {
    notificationsVisible,
    notificationsLoading,
    unreadNotificationCount,
    readNotificationCount,
    notificationReadFilter,
    notificationSummaryText,
    notificationPage,
    loadUnreadNotificationCount,
    openNotifications,
    loadNotifications,
    changeNotificationReadFilter,
    handleNotificationPageChange,
    markNotificationRead,
    markAllNotificationsReadAction,
    clearReadNotificationsAction,
    openNotificationLink,
    startSseSubscription,
    closeSseSubscription,
  }
}
