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
  read?: boolean
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

export async function clearReadNotifications() {
  const { data } = await http.delete<ApiResponse<number>>('/api/notifications/read')
  return data.data
}

export interface NotificationStreamTicket {
  ticket: string
  expiresAt: number
}

export async function createNotificationStreamTicket() {
  const { data } = await http.post<ApiResponse<NotificationStreamTicket>>('/api/notifications/stream-ticket')
  return data.data
}

/**
 * 构造站内通知 SSE 订阅地址。
 * 浏览器原生 EventSource 无法携带自定义 Authorization 头，因此通过短期一次性 ticket 鉴权。
 */
export function buildNotificationStreamUrl(ticket: string): string {
  const base = (http.defaults.baseURL || '').replace(/\/$/, '')
  return `${base}/api/notifications/stream?ticket=${encodeURIComponent(ticket)}`
}