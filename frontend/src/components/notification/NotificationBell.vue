<template>
  <el-tooltip content="站内通知" placement="bottom">
    <el-badge :value="unreadNotificationCount" :max="99" :hidden="unreadNotificationCount === 0" class="notification-badge">
      <el-icon class="action-icon" title="站内通知" @click="openNotifications"><Bell /></el-icon>
    </el-badge>
  </el-tooltip>

  <el-drawer v-model="notificationsVisible" title="站内通知" size="420px">
    <div class="notification-toolbar">
      <span>{{ notificationSummaryText }}</span>
      <div>
        <el-button size="small" text :loading="notificationsLoading" @click="loadNotifications()">刷新</el-button>
        <el-button size="small" type="primary" text :disabled="unreadNotificationCount === 0" @click="markAllNotificationsReadAction">
          全部已读
        </el-button>
      </div>
    </div>
    <el-segmented
      v-model="notificationReadFilter"
      class="notification-filter"
      :options="[
        { label: '全部', value: 'all' },
        { label: `未读 ${unreadNotificationCount}`, value: 'unread' },
      ]"
      @change="changeNotificationReadFilter"
    />
    <div v-loading="notificationsLoading" class="notification-list">
      <el-empty v-if="!notificationPage.records.length" description="暂无站内通知" />
      <article
        v-for="notification in notificationPage.records"
        :key="notification.id"
        class="notification-item"
        :class="{ 'notification-item--unread': !notification.read }"
      >
        <div class="notification-item__head">
          <strong :class="{ 'notification-item__title--link': !!notification.link }" @click="notification.link && openNotificationLink(notification)">
            {{ notification.title }}
          </strong>
          <div class="notification-item__tags">
            <el-tag v-if="notification.level" :type="notificationLevelTagType(notification.level)" effect="plain" size="small">
              {{ notificationLevelLabel(notification.level) }}
            </el-tag>
            <el-tag v-if="notification.scenarioCode" type="info" effect="plain" size="small">
              {{ notificationScenarioLabel(notification.scenarioCode) }}
            </el-tag>
            <el-tag :type="notification.read ? 'info' : 'warning'" effect="plain" size="small">
              {{ notification.read ? '已读' : '未读' }}
            </el-tag>
          </div>
        </div>
        <p>{{ notification.content || '无通知内容' }}</p>
        <div class="notification-item__meta">
          <span>{{ formatDateTime(notification.createdAt) }}</span>
          <div>
            <el-button v-if="!notification.read" link type="primary" @click="markNotificationRead(notification)">标记已读</el-button>
            <el-button v-if="notification.link" link type="primary" @click="openNotificationLink(notification)">查看</el-button>
          </div>
        </div>
      </article>
    </div>
    <el-pagination
      v-if="notificationPage.total > notificationPage.size"
      small
      background
      layout="prev, pager, next"
      class="notification-pagination"
      :current-page="notificationPage.page"
      :page-size="notificationPage.size"
      :total="notificationPage.total"
      @current-change="handleNotificationPageChange"
    />
  </el-drawer>
</template>

<script setup lang="ts">
import type { TagProps } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import { useNotifications } from '@/composables/useNotifications'
import { formatDateTime } from '@/utils/datetime'

const {
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
} = useNotifications()

defineExpose({ loadUnreadNotificationCount })

function notificationLevelTagType(level?: string | null): TagProps['type'] {
  if (level === 'SUCCESS') return 'success'
  if (level === 'WARNING') return 'warning'
  if (level === 'ERROR') return 'danger'
  return 'info'
}

function notificationLevelLabel(level?: string | null) {
  if (level === 'SUCCESS') return '成功'
  if (level === 'WARNING') return '提醒'
  if (level === 'ERROR') return '重要'
  return '通知'
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
</script>

<style scoped lang="scss">
.notification-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -4px 0 12px;
  color: #606b7a;
  font-size: 13px;
}

.notification-filter {
  width: 100%;
  margin-bottom: 12px;
}

.notification-badge :deep(.el-badge__content) {
  transform: translateY(-4px) translateX(4px);
}

.notification-list {
  display: grid;
  gap: 10px;
  min-height: 180px;
}

.notification-item {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #edf0f5;
  border-radius: 12px;
  background: #fff;

  &--unread {
    border-color: rgba(22, 119, 255, 0.26);
    background: #f7fbff;
  }

  &__head,
  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  &__tags {
    display: inline-flex;
    flex: 0 0 auto;
    align-items: center;
    gap: 4px;
  }

  strong {
    min-width: 0;
    color: #1f2937;
    font-size: 14px;
  }

  &__title--link {
    cursor: pointer;

    &:hover {
      color: #1677ff;
    }
  }

  p {
    margin: 0;
    color: #606b7a;
    font-size: 13px;
    line-height: 1.6;
    white-space: pre-wrap;
  }

  &__meta {
    color: #9aa4b2;
    font-size: 12px;
  }
}

.notification-pagination {
  justify-content: flex-end;
  margin-top: 12px;
}
</style>