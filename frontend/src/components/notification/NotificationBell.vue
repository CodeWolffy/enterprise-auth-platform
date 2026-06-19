<template>
  <el-tooltip content="站内通知" placement="bottom">
    <el-badge :value="unreadNotificationCount" :max="99" :hidden="unreadNotificationCount === 0" class="notification-badge">
      <el-icon class="action-icon" title="站内通知" @click="openNotifications"><Bell /></el-icon>
    </el-badge>
  </el-tooltip>

  <el-drawer
    v-model="notificationsVisible"
    direction="rtl"
    size="420px"
    :with-header="false"
    class="notification-drawer"
  >
    <div class="notification-panel">
      <header class="notification-panel__header">
        <div class="notification-panel__title">
          <el-icon class="notification-panel__title-icon"><Bell /></el-icon>
          <span>站内通知</span>
          <el-tag v-if="unreadNotificationCount > 0" type="danger" effect="dark" size="small" round>
            {{ unreadNotificationCount }}
          </el-tag>
        </div>
        <div class="notification-panel__header-actions">
          <el-tooltip content="刷新" placement="bottom">
            <el-icon class="notification-panel__action-icon" :class="{ 'is-loading': notificationsLoading }" @click="loadNotifications()">
              <Refresh />
            </el-icon>
          </el-tooltip>
          <el-tooltip content="全部标记已读" placement="bottom">
            <el-icon
              class="notification-panel__action-icon"
              :class="{ 'is-disabled': unreadNotificationCount === 0 }"
              @click="unreadNotificationCount !== 0 && markAllNotificationsReadAction()"
            >
              <Check />
            </el-icon>
          </el-tooltip>
          <el-tooltip content="清空已读" placement="bottom">
            <el-icon
              class="notification-panel__action-icon notification-panel__action-icon--danger"
              :class="{ 'is-disabled': readNotificationCount === 0 }"
              @click="readNotificationCount !== 0 && clearReadNotificationsAction()"
            >
              <Delete />
            </el-icon>
          </el-tooltip>
          <el-tooltip content="关闭" placement="bottom">
            <el-icon class="notification-panel__action-icon" @click="notificationsVisible = false"><Close /></el-icon>
          </el-tooltip>
        </div>
      </header>

      <div class="notification-panel__filter">
        <button
          v-for="option in filterOptions"
          :key="option.value"
          type="button"
          class="notification-tab"
          :class="{ 'notification-tab--active': notificationReadFilter === option.value }"
          @click="changeNotificationReadFilter(option.value)"
        >
          {{ option.label }}
          <span v-if="option.count > 0" class="notification-tab__count">{{ option.count }}</span>
        </button>
        <span class="notification-panel__summary">{{ notificationSummaryText }}</span>
      </div>

      <div v-loading="notificationsLoading" class="notification-list">
        <el-empty v-if="!notificationPage.records.length" :image-size="80" description="暂无站内通知" class="notification-empty" />
        <article
          v-for="notification in notificationPage.records"
          :key="notification.id"
          class="notification-item"
          :class="[`notification-item--${notificationLevelKey(notification.level)}`, { 'notification-item--unread': !notification.read }]"
          @click="handleNotificationClick(notification)"
        >
          <div class="notification-item__indicator"></div>
          <div class="notification-item__main">
            <div class="notification-item__head">
              <el-icon class="notification-item__level-icon"><component :is="notificationLevelIcon(notification.level)" /></el-icon>
              <strong class="notification-item__title" :class="{ 'notification-item__title--link': !!notification.link }">
                {{ notification.title }}
              </strong>
              <span v-if="!notification.read" class="notification-item__unread-dot"></span>
            </div>
            <p v-if="notification.content" class="notification-item__content">{{ notification.content }}</p>
            <div class="notification-item__footer">
              <div class="notification-item__tags">
                <span v-if="notification.scenarioCode" class="notification-chip">{{ notificationScenarioLabel(notification.scenarioCode) }}</span>
                <span class="notification-item__time">{{ formatRelativeTime(notification.createdAt) }}</span>
              </div>
              <div class="notification-item__actions">
                <el-button
                  v-if="!notification.read"
                  link
                  type="primary"
                  size="small"
                  @click.stop="markNotificationRead(notification)"
                >
                  标为已读
                </el-button>
                <el-button v-if="notification.link" link type="primary" size="small" @click.stop="openNotificationLink(notification)">
                  查看
                </el-button>
              </div>
            </div>
          </div>
        </article>
      </div>

      <footer v-if="notificationPage.total > notificationPage.size" class="notification-panel__footer">
        <el-pagination
          small
          background
          layout="prev, pager, next"
          :current-page="notificationPage.page"
          :page-size="notificationPage.size"
          :total="notificationPage.total"
          @current-change="handleNotificationPageChange"
        />
      </footer>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import { Bell, Check, Close, Delete, Refresh, WarningFilled, SuccessFilled, InfoFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { useNotifications } from '@/composables/useNotifications'
import { formatDateTime } from '@/utils/datetime'
import type { NotificationView } from '@/api/modules'

const {
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
} = useNotifications()

defineExpose({ loadUnreadNotificationCount, startSseSubscription, closeSseSubscription })

const filterOptions = computed(() => [
  { label: '全部', value: 'all' as const, count: 0 },
  { label: '未读', value: 'unread' as const, count: unreadNotificationCount.value },
])

function notificationLevelKey(level?: string | null) {
  if (level === 'SUCCESS') return 'success'
  if (level === 'WARNING') return 'warning'
  if (level === 'ERROR') return 'error'
  return 'info'
}

function notificationLevelIcon(level?: string | null): Component {
  if (level === 'SUCCESS') return SuccessFilled
  if (level === 'WARNING') return WarningFilled
  if (level === 'ERROR') return CircleCloseFilled
  return InfoFilled
}

function notificationScenarioLabel(scenarioCode?: string | null) {
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
  }
  return scenarioCode ? labels[scenarioCode] || scenarioCode : '通知'
}

function formatRelativeTime(epochMs?: number | null) {
  if (!epochMs) return ''
  const now = Date.now()
  const diff = now - epochMs
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDateTime(epochMs)
}

function handleNotificationClick(notification: NotificationView) {
  if (notification.link) {
    openNotificationLink(notification)
  } else if (!notification.read) {
    markNotificationRead(notification)
  }
}
</script>

<style scoped lang="scss">
.notification-badge :deep(.el-badge__content) {
  transform: translateY(-4px) translateX(4px);
}

.notification-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  // 兜底圆角：即使 drawer 的 overflow:hidden 未生效，header/footer 的直角背景
  // 也会被自身的圆角裁剪，保证视觉上始终看到圆角而非直边。
  border-radius: 18px 0 0 18px;
}

.notification-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 14px;
  border-bottom: 1px solid #edf0f5;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  border-radius: 18px 0 0 0;
}

.notification-panel__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 650;
  color: #1f2937;
}

.notification-panel__title-icon {
  font-size: 17px;
  color: #1677ff;
}

.notification-panel__header-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.notification-panel__action-icon {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 16px;
  color: #606b7a;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;

  &:hover {
    color: #1677ff;
    background: #f3f7ff;
  }

  &--danger:hover {
    color: #ef4444;
    background: #fef0f0;
  }

  &.is-loading {
    animation: notification-spin 0.9s linear infinite;
  }

  &.is-disabled {
    opacity: 0.4;
    cursor: not-allowed;
    &:hover {
      color: #606b7a;
      background: transparent;
    }
  }
}

@keyframes notification-spin {
  to { transform: rotate(360deg); }
}

.notification-panel__filter {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 18px 8px;
  border-bottom: 1px solid #f3f5f9;
}

.notification-tab {
  appearance: none;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: #f5f7fb;
  color: #606b7a;
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, border-color 0.2s ease;

  &:hover {
    color: #1677ff;
    background: #eef3fa;
  }

  &--active {
    color: #1677ff;
    background: #e8f3ff;
    border-color: rgba(22, 119, 255, 0.24);
    font-weight: 600;
  }

  &__count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: 999px;
    background: #ff4d4f;
    color: #fff;
    font-size: 11px;
    font-weight: 600;
    line-height: 1;
  }
}

.notification-panel__summary {
  margin-left: auto;
  color: #9aa4b2;
  font-size: 12px;
}

.notification-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notification-empty {
  margin: auto;
  padding: 60px 0;
}

.notification-item {
  position: relative;
  display: flex;
  gap: 10px;
  padding: 12px 12px 12px 14px;
  border: 1px solid #edf0f5;
  border-radius: 10px;
  background: #fff;
  cursor: default;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: #dfe5ee;
    box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05);
  }

  &--unread {
    background: #f7fbff;
    border-color: rgba(22, 119, 255, 0.18);
  }

  &__indicator {
    flex: 0 0 3px;
    align-self: stretch;
    border-radius: 999px;
    background: transparent;
  }

  &--info &__indicator { background: linear-gradient(180deg, #1677ff, #69b1ff); }
  &--success &__indicator { background: linear-gradient(180deg, #16c784, #5fd0a3); }
  &--warning &__indicator { background: linear-gradient(180deg, #f59e0b, #fbbf24); }
  &--error &__indicator { background: linear-gradient(180deg, #ef4444, #f87171); }

  &__main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__level-icon {
    flex: 0 0 auto;
    font-size: 16px;
  }

  &--info &__level-icon { color: #1677ff; }
  &--success &__level-icon { color: #16c784; }
  &--warning &__level-icon { color: #f59e0b; }
  &--error &__level-icon { color: #ef4444; }

  &__title {
    flex: 1;
    min-width: 0;
    color: #1f2937;
    font-size: 14px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__title--link {
    cursor: pointer;
    &:hover { color: #1677ff; }
  }

  &__unread-dot {
    flex: 0 0 8px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #ff4d4f;
    box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.16);
  }

  &__content {
    margin: 0;
    color: #606b7a;
    font-size: 13px;
    line-height: 1.6;
    white-space: pre-wrap;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  &__tags {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__time {
    color: #9aa4b2;
    font-size: 12px;
  }

  &__actions {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    opacity: 0;
    transition: opacity 0.2s ease;
  }

  &:hover &__actions {
    opacity: 1;
  }

  &--unread &__actions {
    opacity: 1;
  }
}

.notification-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 6px;
  background: #f0f4fa;
  color: #606b7a;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.6;
}

.notification-panel__footer {
  display: flex;
  justify-content: center;
  padding: 10px 18px 14px;
  border-top: 1px solid #edf0f5;
  background: #fafbfd;
  border-radius: 0 0 0 18px;
}

.notification-list::-webkit-scrollbar {
  width: 6px;
}
.notification-list::-webkit-scrollbar-thumb {
  background: rgba(22, 119, 255, 0.18);
  border-radius: 999px;
}
.notification-list::-webkit-scrollbar-track {
  background: transparent;
}
</style>

<style lang="scss">
// 站内通知抽屉的圆角与裁剪需要覆盖全局 .el-drawer 样式。
// 1. 移除全局 border-left 直边框，避免遮挡左侧圆角
// 2. 强制 overflow: hidden，确保内部 header/footer 的直角背景被圆角裁剪
// 3. 重置 body 的 display: grid，让 panel 的 flex 满高布局生效
.notification-drawer .el-drawer {
  border-left: none !important;
  border-radius: 18px 0 0 18px !important;
  overflow: hidden !important;
  box-shadow: -12px 0 32px rgba(15, 23, 42, 0.12) !important;
}

.notification-drawer .el-drawer__body {
  padding: 0 !important;
  display: block !important;
  gap: 0 !important;
  height: 100%;
}
</style>
