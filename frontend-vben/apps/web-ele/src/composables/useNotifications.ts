import type { NotificationView } from '#/api/notification';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';
import { useAccessStore } from '@vben/stores';

import {
  buildNotificationStreamUrl,
  clearReadNotifications,
  createNotificationStreamTicket,
  markAllNotificationsRead,
  markNotificationRead as markNotificationReadRequest,
  queryNotifications,
  queryUnreadNotificationCount,
} from '#/api/notification';

export function useNotifications() {
  const router = useRouter();
  const accessStore = useAccessStore();
  const notificationsVisible = ref(false);
  const notificationsLoading = ref(false);
  const unreadNotificationCount = ref(0);
  const notificationReadFilter = ref<'all' | 'unread'>('all');
  const notificationPage = ref<{
    total: number;
    page: number;
    size: number;
    records: NotificationView[];
  }>({
    total: 0,
    page: 1,
    size: 8,
    records: [],
  });

  const notificationSummaryText = computed(() => {
    if (notificationReadFilter.value === 'unread') {
      return `未读 ${notificationPage.value.total} 条 / 全部未读 ${unreadNotificationCount.value} 条`;
    }
    return `共 ${notificationPage.value.total} 条通知，未读 ${unreadNotificationCount.value} 条`;
  });

  const readNotificationCount = computed(() =>
    Math.max(notificationPage.value.total - unreadNotificationCount.value, 0),
  );

  let sseSource: EventSource | null = null;
  let sseReconnectTimer: ReturnType<typeof setTimeout> | null = null;
  let sseManualClose = false;

  async function loadUnreadNotificationCount() {
    try {
      unreadNotificationCount.value = await queryUnreadNotificationCount();
    } catch {
      unreadNotificationCount.value = 0;
    }
  }

  async function openNotifications() {
    notificationsVisible.value = true;
    notificationPage.value.page = 1;
    await loadNotifications();
  }

  async function loadNotifications(page = notificationPage.value.page) {
    notificationsLoading.value = true;
    try {
      notificationPage.value = await queryNotifications({
        page,
        size: notificationPage.value.size,
        read: notificationReadFilter.value === 'unread' ? false : undefined,
      });
      await loadUnreadNotificationCount();
    } finally {
      notificationsLoading.value = false;
    }
  }

  async function changeNotificationReadFilter(filter: 'all' | 'unread') {
    notificationReadFilter.value = filter;
    notificationPage.value.page = 1;
    await loadNotifications(1);
  }

  async function handleNotificationPageChange(page: number) {
    notificationPage.value.page = page;
    await loadNotifications(page);
  }

  async function markNotificationRead(notification: NotificationView) {
    if (notification.read) {
      return;
    }
    const updated = await markNotificationReadRequest(notification.id);
    if (notificationReadFilter.value === 'unread') {
      notificationPage.value.records = notificationPage.value.records.filter((item) => item.id !== updated.id);
      notificationPage.value.total = Math.max(notificationPage.value.total - 1, 0);
    } else {
      notificationPage.value.records = notificationPage.value.records.map((item) =>
        item.id === updated.id ? updated : item,
      );
    }
    await loadUnreadNotificationCount();
  }

  async function markAllNotificationsReadAction() {
    const changed = await markAllNotificationsRead();
    if (changed > 0) {
      ElMessage.success('站内通知已全部标记为已读');
    }
    notificationPage.value.page = 1;
    await loadNotifications(1);
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
        },
      );
    } catch {
      return;
    }
    const removed = await clearReadNotifications();
    if (removed > 0) {
      ElMessage.success(`已清空 ${removed} 条已读通知`);
    } else {
      ElMessage.info('没有可清空的已读通知');
    }
    notificationPage.value.page = 1;
    await loadNotifications(1);
  }

  async function openNotificationLink(notification: NotificationView) {
    await markNotificationRead(notification);
    if (!notification.link) {
      return;
    }
    notificationsVisible.value = false;
    await router.push(notification.link);
  }

  function handleWindowFocus() {
    void loadUnreadNotificationCount();
  }

  async function startSseSubscription() {
    closeSseSubscription();
    if (!accessStore.accessToken) {
      return;
    }
    sseManualClose = false;
    let streamTicket: string;
    try {
      streamTicket = (await createNotificationStreamTicket()).ticket;
    } catch {
      scheduleSseReconnect();
      return;
    }
    if (sseManualClose || !accessStore.accessToken) {
      return;
    }
    try {
      sseSource = new EventSource(buildNotificationStreamUrl(streamTicket));
    } catch {
      scheduleSseReconnect();
      return;
    }
    sseSource.addEventListener('open', () => {
      void loadUnreadNotificationCount();
    });
    sseSource.addEventListener('notification', (event) => {
      try {
        const notification = JSON.parse((event as MessageEvent).data) as NotificationView;
        handleIncomingNotification(notification);
      } catch {
        // ignore malformed event
      }
    });
    sseSource.addEventListener('error', () => {
      closeSseSourceOnly();
      if (!sseManualClose) {
        scheduleSseReconnect();
      }
    });
  }

  function closeSseSourceOnly() {
    if (sseSource) {
      sseSource.close();
      sseSource = null;
    }
  }

  function closeSseSubscription() {
    sseManualClose = true;
    if (sseReconnectTimer) {
      clearTimeout(sseReconnectTimer);
      sseReconnectTimer = null;
    }
    closeSseSourceOnly();
  }

  function scheduleSseReconnect() {
    if (sseReconnectTimer || sseManualClose) {
      return;
    }
    sseReconnectTimer = setTimeout(() => {
      sseReconnectTimer = null;
      if (!sseManualClose && accessStore.accessToken) {
        void startSseSubscription();
      }
    }, 5000);
  }

  function handleIncomingNotification(notification: NotificationView) {
    unreadNotificationCount.value += 1;
    if (notificationsVisible.value && notificationReadFilter.value === 'all') {
      const exists = notificationPage.value.records.some((item) => item.id === notification.id);
      if (!exists) {
        notificationPage.value.records = [notification, ...notificationPage.value.records].slice(0, notificationPage.value.size);
        notificationPage.value.total += 1;
      }
    }
    showNotificationToast(notification);
  }

  function showNotificationToast(notification: NotificationView) {
    const level = notification.level;
    const type: 'success' | 'warning' | 'error' | 'info' =
      level === 'SUCCESS' ? 'success' :
      level === 'WARNING' ? 'warning' :
      level === 'ERROR' ? 'error' : 'info';
    ElMessage({
      type,
      message: notification.title,
      duration: 4500,
      grouping: true,
      offset: 24,
    });
  }

  onMounted(() => {
    window.addEventListener('focus', handleWindowFocus);
    void startSseSubscription();
  });

  onBeforeUnmount(() => {
    window.removeEventListener('focus', handleWindowFocus);
    closeSseSubscription();
  });

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
  };
}
