import { onBeforeUnmount, onMounted, ref } from 'vue'
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
  const notificationPage = ref<{ total: number; page: number; size: number; records: NotificationView[] }>({
    total: 0,
    page: 1,
    size: 8,
    records: [],
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
      notificationPage.value = await queryNotifications({ page, size: notificationPage.value.size })
      await loadUnreadNotificationCount()
    } finally {
      notificationsLoading.value = false
    }
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
    notificationPage.value.records = notificationPage.value.records.map((item) => item.id === updated.id ? updated : item)
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
    notificationPage,
    loadUnreadNotificationCount,
    openNotifications,
    loadNotifications,
    handleNotificationPageChange,
    markNotificationRead,
    markAllNotificationsReadAction,
    openNotificationLink,
  }
}