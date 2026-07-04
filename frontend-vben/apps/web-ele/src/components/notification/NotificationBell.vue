<script setup lang="ts">
import type { Component } from 'vue';

import type { NotificationView } from '#/api/notification';

import { computed } from 'vue';

import {
  Bell,
  Check,
  CircleCloseFilled,
  Close,
  Delete,
  InfoFilled,
  Refresh,
  SuccessFilled,
  WarningFilled,
} from '@element-plus/icons-vue';
import {
  ElBadge,
  ElButton,
  ElDrawer,
  ElEmpty,
  ElIcon,
  ElPagination,
  ElTag,
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
} = useNotifications();

defineExpose({
  loadUnreadNotificationCount,
  startSseSubscription,
  closeSseSubscription,
});

const filterOptions = computed(() => [
  { label: '全部', value: 'all' as const, count: 0 },
  {
    label: '未读',
    value: 'unread' as const,
    count: unreadNotificationCount.value,
  },
]);

const panelSummaryText = computed(() => {
  if (notificationReadFilter.value === 'unread') {
    return `未读 ${notificationPage.value.total} 条`;
  }
  return `共 ${notificationPage.value.total} 条`;
});

function notificationLevelKey(level?: null | string) {
  if (level === 'SUCCESS') return 'success';
  if (level === 'WARNING') return 'warning';
  if (level === 'ERROR') return 'error';
  return 'info';
}

function notificationLevelIcon(level?: null | string): Component {
  if (level === 'SUCCESS') return SuccessFilled;
  if (level === 'WARNING') return WarningFilled;
  if (level === 'ERROR') return CircleCloseFilled;
  return InfoFilled;
}

function notificationScenarioLabel(scenarioCode?: null | string) {
  const labels: Record<string, string> = {
    WORKFLOW_TODO_CREATED: '新待办',
    WORKFLOW_TASK_APPROVED: '审批通过',
    WORKFLOW_TASK_REJECTED: '审批驳回',
    WORKFLOW_TASK_TRANSFERRED: '转签',
    WORKFLOW_INSTANCE_WITHDRAWN: '已撤回',
    WORKFLOW_INSTANCE_TERMINATED: '已终止',
    ACCOUNT_LOCKED: '账号锁定',
    PASSWORD_RESET_REQUESTED: '重置请求',
    PASSWORD_RESET_COMPLETED: '重置完成',
    PASSWORD_CHANGED: '密码修改',
    ADMIN_PASSWORD_RESET: '管理员重置',
    ACCOUNT_DISABLED: '账号禁用',
    SESSION_FORCED_OFFLINE: '强制下线',
    SYSTEM_NOTICE_PUBLISHED: '系统公告',
  };
  return scenarioCode ? labels[scenarioCode] || scenarioCode : '通知';
}

function handleNotificationClick(notification: NotificationView) {
  if (notification.link) {
    openNotificationLink(notification);
  } else if (!notification.read) {
    markNotificationRead(notification);
  }
}
</script>

<template>
  <ElTooltip content="站内通知" placement="bottom">
    <ElBadge
      :value="unreadNotificationCount"
      :max="99"
      :hidden="unreadNotificationCount === 0"
      class="notification-badge"
    >
      <ElIcon class="action-icon" title="站内通知" @click="openNotifications">
        <Bell />
      </ElIcon>
    </ElBadge>
  </ElTooltip>

  <ElDrawer
    v-model="notificationsVisible"
    direction="rtl"
    size="400px"
    :with-header="false"
    append-to-body
    class="notification-drawer"
  >
    <div class="notification-panel">
      <header class="notification-panel__header">
        <div class="notification-panel__title">
          <ElIcon class="notification-panel__title-icon"><Bell /></ElIcon>
          <span>站内通知</span>
          <ElTag
            v-if="unreadNotificationCount > 0"
            type="danger"
            effect="plain"
            size="small"
            round
          >
            {{ unreadNotificationCount }}
          </ElTag>
        </div>
        <div class="notification-panel__header-actions">
          <ElTooltip content="刷新" placement="bottom">
            <ElIcon
              class="notification-panel__action-icon"
              :class="{ 'is-loading': notificationsLoading }"
              @click="loadNotifications()"
            >
              <Refresh />
            </ElIcon>
          </ElTooltip>
          <ElTooltip content="全部标记已读" placement="bottom">
            <ElIcon
              class="notification-panel__action-icon"
              :class="{ 'is-disabled': unreadNotificationCount === 0 }"
              @click="
                unreadNotificationCount !== 0 &&
                markAllNotificationsReadAction()
              "
            >
              <Check />
            </ElIcon>
          </ElTooltip>
          <ElTooltip content="清空已读" placement="bottom">
            <ElIcon
              class="notification-panel__action-icon notification-panel__action-icon--danger"
              :class="{ 'is-disabled': readNotificationCount === 0 }"
              @click="
                readNotificationCount !== 0 && clearReadNotificationsAction()
              "
            >
              <Delete />
            </ElIcon>
          </ElTooltip>
          <ElTooltip content="关闭" placement="bottom">
            <ElIcon
              class="notification-panel__action-icon"
              @click="notificationsVisible = false"
            >
              <Close />
            </ElIcon>
          </ElTooltip>
        </div>
      </header>

      <div class="notification-panel__filter">
        <button
          v-for="option in filterOptions"
          :key="option.value"
          type="button"
          class="notification-tab"
          :class="{
            'notification-tab--active': notificationReadFilter === option.value,
          }"
          @click="changeNotificationReadFilter(option.value)"
        >
          {{ option.label }}
          <span v-if="option.count > 0" class="notification-tab__count">{{
            option.count
          }}</span>
        </button>
        <span class="notification-panel__summary">{{ panelSummaryText }}</span>
      </div>

      <div v-loading="notificationsLoading" class="notification-list">
        <ElEmpty
          v-if="notificationPage.records.length === 0"
          :image-size="80"
          description="暂无站内通知"
          class="notification-empty"
        />
        <article
          v-for="notification in notificationPage.records"
          :key="notification.id"
          class="notification-item"
          :class="[
            `notification-item--${notificationLevelKey(notification.level)}`,
            { 'notification-item--unread': !notification.read },
          ]"
          @click="handleNotificationClick(notification)"
        >
          <div class="notification-item__main">
            <div class="notification-item__head">
              <ElIcon class="notification-item__level-icon">
                <component :is="notificationLevelIcon(notification.level)" />
              </ElIcon>
              <strong
                class="notification-item__title"
                :class="{
                  'notification-item__title--link': !!notification.link,
                }"
              >
                {{ notification.title }}
              </strong>
              <span
                v-if="!notification.read"
                class="notification-item__unread-dot"
              ></span>
            </div>
            <p v-if="notification.content" class="notification-item__content">
              {{ notification.content }}
            </p>
            <div class="notification-item__footer">
              <div class="notification-item__tags">
                <span
                  v-if="notification.scenarioCode"
                  class="notification-chip"
                >
                  {{ notificationScenarioLabel(notification.scenarioCode) }}
                </span>
                <span class="notification-item__time">
                  {{ formatRelativeTime(notification.createdAt) }}
                </span>
              </div>
              <div class="notification-item__actions">
                <ElButton
                  v-if="!notification.read"
                  link
                  type="primary"
                  size="small"
                  @click.stop="markNotificationRead(notification)"
                >
                  标为已读
                </ElButton>
                <ElButton
                  v-if="notification.link"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openNotificationLink(notification)"
                >
                  查看
                </ElButton>
              </div>
            </div>
          </div>
        </article>
      </div>

      <footer
        v-if="notificationPage.total > notificationPage.size"
        class="notification-panel__footer"
      >
        <ElPagination
          size="small"
          background
          layout="prev, pager, next"
          :current-page="notificationPage.page"
          :page-size="notificationPage.size"
          :total="notificationPage.total"
          @current-change="handleNotificationPageChange"
        />
      </footer>
    </div>
  </ElDrawer>
</template>

<style scoped lang="scss">
.notification-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-right: 4px;
}

.notification-badge :deep(.el-badge__content) {
  transform: translateY(-4px) translateX(4px);
}

.action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  color: hsl(var(--foreground) / 80%);
  cursor: pointer;
  border-radius: 999px;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.action-icon:hover {
  color: hsl(var(--foreground));
  background: hsl(var(--accent));
}

.notification-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: hsl(var(--background));
}

.notification-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 52px;
  padding: 0 16px;
  background: hsl(var(--background));
  border-bottom: 1px solid hsl(var(--border));
}

.notification-panel__title {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: hsl(var(--foreground));
}

.notification-panel__title-icon {
  font-size: 17px;
  color: hsl(var(--primary));
}

.notification-panel__header-actions {
  display: inline-flex;
  gap: 2px;
  align-items: center;
}

.notification-panel__action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  font-size: 15px;
  color: hsl(var(--muted-foreground));
  cursor: pointer;
  border-radius: 6px;
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.notification-panel__action-icon:hover {
  color: hsl(var(--foreground));
  background: hsl(var(--accent));
}

.notification-panel__action-icon--danger:hover {
  color: hsl(var(--destructive));
}

.notification-panel__action-icon.is-loading {
  animation: notification-spin 0.9s linear infinite;
}

.notification-panel__action-icon.is-disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

@keyframes notification-spin {
  to {
    transform: rotate(360deg);
  }
}

.notification-panel__filter {
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 46px;
  padding: 0 16px;
  background: hsl(var(--background));
  border-bottom: 1px solid hsl(var(--border));
}

.notification-tab {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  color: hsl(var(--muted-foreground));
  appearance: none;
  cursor: pointer;
  background: hsl(var(--background));
  border: 1px solid hsl(var(--border));
  border-radius: 6px;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.notification-tab--active {
  font-weight: 600;
  color: hsl(var(--primary));
  background: hsl(var(--primary) / 8%);
  border-color: hsl(var(--primary) / 28%);
}

.notification-tab__count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  color: hsl(var(--destructive-foreground));
  background: hsl(var(--destructive));
  border-radius: 999px;
}

.notification-panel__summary {
  margin-left: auto;
  font-size: 12px;
  color: hsl(var(--muted-foreground));
  white-space: nowrap;
}

.notification-list {
  flex: 1;
  padding: 0;
  overflow-y: auto;
}

.notification-empty {
  padding: 60px 0;
  margin: auto;
}

.notification-item {
  position: relative;
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  background: hsl(var(--background));
  border-bottom: 1px solid hsl(var(--border));
  transition: background-color 0.2s ease;
}

.notification-item:hover {
  background: hsl(var(--accent) / 50%);
}

.notification-item--unread {
  background: hsl(var(--primary) / 3%);
}

.notification-item__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.notification-item__head {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.notification-item__level-icon {
  flex: 0 0 auto;
  font-size: 15px;
}

.notification-item--info .notification-item__level-icon {
  color: hsl(var(--primary));
}

.notification-item--success .notification-item__level-icon {
  color: var(--el-color-success);
}

.notification-item--warning .notification-item__level-icon {
  color: var(--el-color-warning);
}

.notification-item--error .notification-item__level-icon {
  color: hsl(var(--destructive));
}

.notification-item__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 600;
  color: hsl(var(--foreground));
  white-space: nowrap;
}

.notification-item__title--link {
  cursor: pointer;
}

.notification-item__unread-dot {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  background: hsl(var(--destructive));
  border-radius: 50%;
}

.notification-item__content {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 13px;
  line-height: 1.5;
  color: hsl(var(--muted-foreground));
  white-space: pre-wrap;
  -webkit-box-orient: vertical;
}

.notification-item__footer {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.notification-item__tags {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.notification-item__time {
  font-size: 12px;
  color: hsl(var(--muted-foreground));
}

.notification-item__actions {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.notification-item:hover .notification-item__actions {
  opacity: 1;
}

.notification-item--unread .notification-item__actions {
  opacity: 1;
}

.notification-chip {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  color: hsl(var(--muted-foreground));
  background: hsl(var(--muted) / 45%);
  border: 1px solid hsl(var(--border));
  border-radius: 4px;
}

.notification-panel__footer {
  display: flex;
  justify-content: center;
  padding: 12px 16px;
  background: hsl(var(--background));
  border-top: 1px solid hsl(var(--border));
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
</style>

<style lang="scss">
.notification-drawer.el-drawer,
.notification-drawer .el-drawer {
  overflow: hidden !important;
  border-left: 1px solid hsl(var(--border)) !important;
  border-radius: 0 !important;
  box-shadow: -8px 0 24px hsl(var(--foreground) / 8%) !important;
}

.notification-drawer.el-drawer .el-drawer__body,
.notification-drawer .el-drawer__body {
  display: block !important;
  gap: 0 !important;
  height: 100%;
  padding: 0 !important;
}
</style>
