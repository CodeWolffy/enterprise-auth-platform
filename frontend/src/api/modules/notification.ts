import { http } from '../http'
import type { ApiResponse, PageResult } from '@/types/api'

export interface NotificationView {
  id: number
  scenarioCode?: string | null
  sourceType?: string | null
  sourceId?: string | null
  bizType?: string | null
  bizId?: string | null
  title: string
  content?: string | null
  level?: 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR' | string | null
  link?: string | null
  actionPayload?: string | null
  metadata?: string | null
  read: boolean
  readAt?: number | null
  expiresAt?: number | null
  createdAt?: number | null
}

export interface NotificationQueryParams {
  page?: number
  size?: number
}

export async function queryNotifications(params?: NotificationQueryParams) {
  const { data } = await http.get<ApiResponse<PageResult<NotificationView>>>('/api/notifications', { params })
  return data.data
}

export async function queryUnreadNotificationCount() {
  const { data } = await http.get<ApiResponse<number>>('/api/notifications/unread-count')
  return data.data
}

export async function markNotificationRead(notificationId: number) {
  const { data } = await http.put<ApiResponse<NotificationView>>(`/api/notifications/${notificationId}/read`)
  return data.data
}

export async function markAllNotificationsRead() {
  const { data } = await http.put<ApiResponse<number>>('/api/notifications/read-all')
  return data.data
}