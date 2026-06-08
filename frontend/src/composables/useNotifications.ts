import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  markAllNotificationsRead,
  markNotificationRead as markNotificationReadRequest,
  queryNotifications,
  queryUnreadNotificationCount,
  type NotificationView,
} from '@/api/modules'

export function useNotifications() {
  const router = useRouter()
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

  onMounted(() => {
    window.addEventListener('focus', handleWindowFocus)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('focus', handleWindowFocus)
  })

  return {
    notificationsVisible,
    notificationsLoading,
    unreadNotificationCount,
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
    openNotificationLink,
  }
}