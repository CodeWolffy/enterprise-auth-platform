import { onBeforeUnmount, onMounted } from 'vue';

import { storeToRefs } from 'pinia';

import { useNotificationStore } from '#/store/notification';

/**
 * 站内通知薄包装 composable。
 *
 * 状态与业务逻辑已迁移到 Pinia store（#/store/notification），
 * 本包装保留原有同名 API 并负责组件生命周期内的
 * SSE 订阅启动/断开与窗口聚焦刷新，行为与迁移前一致。
 */
export function useNotifications() {
  const store = useNotificationStore();

  const {
    notificationsVisible,
    notificationsLoading,
    unreadNotificationCount,
    readNotificationCount,
    notificationReadFilter,
    notificationSummaryText,
    notificationPage,
  } = storeToRefs(store);

  onMounted(() => {
    window.addEventListener('focus', store.handleWindowFocus);
    void store.startSseSubscription();
  });

  onBeforeUnmount(() => {
    window.removeEventListener('focus', store.handleWindowFocus);
    store.closeSseSubscription();
  });

  return {
    notificationsVisible,
    notificationsLoading,
    unreadNotificationCount,
    readNotificationCount,
    notificationReadFilter,
    notificationSummaryText,
    notificationPage,
    loadUnreadNotificationCount: store.loadUnreadNotificationCount,
    openNotifications: store.openNotifications,
    loadNotifications: store.loadNotifications,
    changeNotificationReadFilter: store.changeNotificationReadFilter,
    handleNotificationPageChange: store.handleNotificationPageChange,
    markNotificationRead: store.markNotificationRead,
    markAllNotificationsReadAction: store.markAllNotificationsReadAction,
    clearReadNotificationsAction: store.clearReadNotificationsAction,
    openNotificationLink: store.openNotificationLink,
    startSseSubscription: store.startSseSubscription,
    closeSseSubscription: store.closeSseSubscription,
  };
}
