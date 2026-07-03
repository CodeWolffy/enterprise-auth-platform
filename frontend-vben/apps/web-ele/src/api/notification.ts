import type { PageResult } from '#/types/api';

import { requestClient } from '#/api/request';

export interface NotificationView {
  id: number;
  scenarioCode?: string | null;
  sourceType?: string | null;
  sourceId?: string | null;
  bizType?: string | null;
  bizId?: string | null;
  title: string;
  content?: string | null;
  level?: 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR' | string | null;
  link?: string | null;
  actionPayload?: string | null;
  metadata?: string | null;
  read: boolean;
  readAt?: string | null;
  expiresAt?: string | null;
  createdAt?: string | null;
}

export interface NotificationQueryParams {
  page?: number;
  size?: number;
  read?: boolean;
}

export interface NotificationStreamTicket {
  ticket: string;
  expiresAt: string;
}

export async function queryNotifications(params?: NotificationQueryParams) {
  return await requestClient.get<PageResult<NotificationView>>('/notifications', {
    params,
  });
}

export async function queryUnreadNotificationCount() {
  return await requestClient.get<number>('/notifications/unread-count');
}

export async function markNotificationRead(notificationId: number) {
  return await requestClient.put<NotificationView>(`/notifications/${notificationId}/read`);
}

export async function markAllNotificationsRead() {
  return await requestClient.put<number>('/notifications/read-all');
}

export async function clearReadNotifications() {
  return await requestClient.delete<number>('/notifications/read');
}

export async function createNotificationStreamTicket() {
  return await requestClient.post<NotificationStreamTicket>('/notifications/stream-ticket');
}

export function buildNotificationStreamUrl(ticket: string): string {
  const base = (import.meta.env.VITE_GLOB_API_URL || '/api').replace(/\/$/, '');
  return `${base}/notifications/stream?ticket=${encodeURIComponent(ticket)}`;
}
