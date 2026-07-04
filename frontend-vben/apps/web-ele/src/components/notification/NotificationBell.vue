<script setup lang="ts">
import type { NotificationView } from '#/api/notification';

import { computed } from 'vue';

import { Bell, CircleCheckBig, MailCheck } from '@vben/icons';

import {
  ElButton,
  ElEmpty,
  ElPopover,
  ElTooltip,
} from 'element-plus';

import { useNotifications } from '#/composables/useNotifications';
import { formatRelativeTime } from '#/utils/datetime';

const {
  notificationsVisible,
  notificationsLoading,
  unreadNotificationCount,
  readNotificationCount,
  notificationReadFilter,
  notificationPage,
  loadUnreadNotificationCount,
  loadNotifications,
  changeNotificationReadFilter,
  markNotificationRead,
  markAllNotificationsReadAction,
  clearReadNotificationsAction,
  openNotificationLink,
  startSseSubscription,
  closeSseSubscription,
} = useNotifications();

defineExpose({
  loadUnreadNotificationCount,
  startSseSubscription,
  closeSseSubscription,
});

const notificationTotalText = computed(() => {
  if (notificationReadFilter.value === 'unread') {
    return `未读 ${notificationPage.value.total} 条`;
  }
  return notificationPage.value.total > 0
    ? `共 ${notificationPage.value.total} 条`
    : '';
});

function notificationAvatarClass(notification: NotificationView) {
  if (notification.level === 'ERROR') return 'is-error';
  if (notification.level === 'WARNING') return 'is-warning';
  if (notification.level === 'SUCCESS') return 'is-success';
  return 'is-info';
}

function notificationAvatarText(notification: NotificationView) {
  if (notification.scenarioCode === 'SYSTEM_NOTICE_PUBLISHED') {
    return 'VB';
  }
  return notification.title?.trim().slice(0, 1).toUpperCase() || 'VB';
}

function handlePopoverShow() {
  void loadNotifications(1);
}

function handleNotificationClick(notification: NotificationView) {
  if (notification.link) {
    openNotificationLink(notification);
  } else if (!notification.read) {
    markNotificationRead(notification);
  }
}

async function handleViewAllMessages() {
  if (notificationReadFilter.value !== 'all') {
    await changeNotificationReadFilter('all');
    return;
  }
  await loadNotifications(1);
}
</script>

<template>
  <ElPopover
    v-model:visible="notificationsVisible"
    placement="bottom-end"
    trigger="click"
    :width="540"
    :teleported="true"
    popper-class="vben-notification-popover"
    @show="handlePopoverShow"
  >
    <template #reference>
      <button
        aria-label="站内通知"
        class="notification-trigger"
        title="站内通知"
        type="button"
      >
        <span
          v-if="unreadNotificationCount > 0"
          class="notification-trigger__dot"
        ></span>
        <Bell class="notification-trigger__icon" />
      </button>
    </template>

    <section class="notification-panel">
      <header class="notification-panel__header">
        <div class="notification-panel__title">
          <span>通知</span>
          <span
            v-if="notificationTotalText"
            class="notification-panel__summary"
          >
            {{ notificationTotalText }}
          </span>
        </div>
        <ElTooltip content="全部标记已读" placement="bottom">
          <button
            class="notification-panel__icon-button"
            :class="{ 'is-disabled': unreadNotificationCount === 0 }"
            type="button"
            :disabled="unreadNotificationCount === 0"
            @click="markAllNotificationsReadAction"
          >
            <MailCheck />
          </button>
        </ElTooltip>
      </header>

      <div v-loading="notificationsLoading" class="notification-list">
        <ElEmpty
          v-if="notificationPage.records.length === 0"
          :image-size="76"
          description="暂无通知"
          class="notification-empty"
        />
        <article
          v-for="notification in notificationPage.records"
          :key="notification.id"
          class="notification-item"
          :class="{ 'notification-item--unread': !notification.read }"
          @click="handleNotificationClick(notification)"
        >
          <span
            class="notification-avatar"
            :class="notificationAvatarClass(notification)"
          >
            {{ notificationAvatarText(notification) }}
          </span>

          <div class="notification-item__body">
            <strong class="notification-item__title">
              {{ notification.title }}
            </strong>
            <p v-if="notification.content" class="notification-item__content">
              {{ notification.content }}
            </p>
            <p class="notification-item__time">
              {{ formatRelativeTime(notification.createdAt) }}
            </p>
          </div>

          <span
            v-if="!notification.read"
            class="notification-item__unread-dot"
          ></span>
          <ElTooltip
            v-if="!notification.read"
            content="标记为已读"
            placement="left"
          >
            <button
              class="notification-item__action"
              type="button"
              @click.stop="markNotificationRead(notification)"
            >
              <CircleCheckBig class="notification-item__action-icon" />
            </button>
          </ElTooltip>
        </article>
      </div>

      <footer class="notification-panel__footer">
        <ElButton
          link
          class="notification-panel__clear"
          :disabled="readNotificationCount === 0"
          @click="clearReadNotificationsAction"
        >
          清空
        </ElButton>
        <ElButton type="primary" @click="handleViewAllMessages">
          查看所有消息
        </ElButton>
      </footer>
    </section>
  </ElPopover>
</template>

<style scoped lang="scss">
.notification-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-right: 4px;
  padding: 0;
  color: hsl(var(--foreground) / 86%);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  outline: none;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;

  &:hover {
    color: hsl(var(--foreground));
    background: hsl(var(--accent));

    .notification-trigger__icon {
      animation: notification-bell-ring 1s both;
    }
  }
}

.notification-trigger__icon {
  width: 18px;
  height: 18px;
}

.notification-trigger__dot {
  position: absolute;
  top: 3px;
  right: 4px;
  z-index: 1;
  width: 8px;
  height: 8px;
  background: #1677ff;
  border-radius: 999px;
}

.notification-panel {
  overflow: hidden;
  color: hsl(var(--foreground));
  background: hsl(var(--background));
  border-radius: 6px;
}

.notification-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 84px;
  padding: 0 24px;
  border-bottom: 1px solid hsl(var(--border));
}

.notification-panel__title {
  display: inline-flex;
  gap: 10px;
  align-items: baseline;
  font-size: 20px;
  font-weight: 500;
}

.notification-panel__summary {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.notification-panel__icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: hsl(var(--foreground));
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  outline: none;
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    opacity 0.2s ease;

  svg {
    width: 22px;
    height: 22px;
  }

  &:hover:not(:disabled) {
    background: hsl(var(--accent));
  }

  &.is-disabled,
  &:disabled {
    cursor: not-allowed;
    opacity: 0.35;
  }
}

.notification-list {
  max-height: 532px;
  overflow-y: auto;
}

.notification-empty {
  padding: 64px 0;
}

.notification-item {
  position: relative;
  display: flex;
  gap: 30px;
  align-items: flex-start;
  min-height: 132px;
  padding: 20px 68px 18px 18px;
  cursor: pointer;
  border-bottom: 1px solid hsl(var(--border));
  transition: background-color 0.18s ease;

  &:hover {
    background: hsl(var(--accent) / 68%);
  }
}

.notification-avatar {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  margin-top: 2px;
  overflow: hidden;
  font-size: 25px;
  font-weight: 400;
  line-height: 1;
  color: white;
  border-radius: 999px;

  &.is-info,
  &.is-success {
    background: linear-gradient(135deg, #a8ff35 0%, #23d7d7 100%);
  }

  &.is-warning {
    background: linear-gradient(135deg, #f7dd4a 0%, #20c6dc 100%);
  }

  &.is-error {
    background: linear-gradient(135deg, #2915c9 0%, #e20040 100%);
  }
}

.notification-item__body {
  min-width: 0;
}

.notification-item__title {
  display: block;
  max-width: 300px;
  overflow: hidden;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.24;
  color: hsl(var(--foreground));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-item__content {
  display: -webkit-box;
  max-width: 300px;
  margin: 8px 0 0;
  overflow: hidden;
  -webkit-line-clamp: 1;
  font-size: 16px;
  line-height: 1.45;
  color: hsl(var(--muted-foreground));
  -webkit-box-orient: vertical;
}

.notification-item__time {
  margin: 12px 0 0;
  font-size: 16px;
  line-height: 1.35;
  color: hsl(var(--muted-foreground));
}

.notification-item__unread-dot {
  position: absolute;
  top: 12px;
  right: 13px;
  width: 12px;
  height: 12px;
  background: #1677ff;
  border-radius: 999px;
}

.notification-item__action {
  position: absolute;
  top: 50%;
  right: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  color: hsl(var(--foreground));
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 999px;
  outline: none;
  transform: translateY(-50%);
  transition:
    background-color 0.2s ease,
    color 0.2s ease;

  &:hover {
    color: hsl(var(--primary));
    background: hsl(var(--accent));
  }
}

.notification-item__action-icon {
  width: 24px;
  height: 24px;
}

.notification-panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 84px;
  padding: 0 24px 0 36px;
  border-top: 1px solid hsl(var(--border));
}

.notification-panel__clear {
  font-size: 18px;
  font-weight: 600;
  color: hsl(var(--foreground));

  &.is-disabled,
  &:disabled {
    color: hsl(var(--muted-foreground));
  }
}

.notification-list::-webkit-scrollbar {
  width: 6px;
}

.notification-list::-webkit-scrollbar-thumb {
  background: hsl(var(--border));
  border-radius: 999px;
}

.notification-list::-webkit-scrollbar-track {
  background: transparent;
}

@keyframes notification-bell-ring {
  0%,
  100% {
    transform-origin: top;
  }

  15% {
    transform: rotateZ(10deg);
  }

  30% {
    transform: rotateZ(-10deg);
  }

  45% {
    transform: rotateZ(5deg);
  }

  60% {
    transform: rotateZ(-5deg);
  }

  75% {
    transform: rotateZ(2deg);
  }
}
</style>

<style lang="scss">
.vben-notification-popover.el-popper {
  padding: 0 !important;
  overflow: hidden;
  border: 1px solid hsl(var(--border)) !important;
  border-radius: 6px !important;
  box-shadow:
    0 10px 15px -3px hsl(var(--foreground) / 10%),
    0 4px 6px -4px hsl(var(--foreground) / 10%) !important;
}

.vben-notification-popover.el-popper .el-popper__arrow {
  display: none;
}
</style>
