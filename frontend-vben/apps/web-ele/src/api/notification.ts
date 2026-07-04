import type { ScopedRequestConfig } from '#/api/request';
import type { PageResult } from '#/types/api';

import { requestClient } from '#/api/request';

export interface NotificationView {
  id: number;
  scenarioCode?: null | string;
  sourceType?: null | string;
  sourceId?: null | string;
  bizType?: null | string;
  bizId?: null | string;
  title: string;
  content?: null | string;
  level?: 'ERROR' | 'INFO' | 'SUCCESS' | 'WARNING' | null | string;
  link?: null | string;
  actionPayload?: null | string;
  metadata?: null | string;
  read: boolean;
  readAt?: null | string;
  expiresAt?: null | string;
  createdAt?: null | string;
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

function withSilentNotificationError(
  config: ScopedRequestConfig = {},
): ScopedRequestConfig {
  return {
    ...config,
    suppressErrorMessage: true,
  };
}

export async function queryNotifications(params?: NotificationQueryParams) {
  return await requestClient.get<PageResult<NotificationView>>(
    '/notifications',
    withSilentNotificationError({ params }),
  );
}

export async function queryUnreadNotificationCount() {
  return await requestClient.get<number>(
    '/notifications/unread-count',
    withSilentNotificationError(),
  );
}

export async function markNotificationRead(notificationId: number) {
  return await requestClient.put<NotificationView>(
    `/notifications/${notificationId}/read`,
    undefined,
    withSilentNotificationError(),
  );
}

export async function markAllNotificationsRead() {
  return await requestClient.put<number>(
    '/notifications/read-all',
    undefined,
    withSilentNotificationError(),
  );
}

export async function clearReadNotifications() {
  return await requestClient.delete<number>(
    '/notifications/read',
    withSilentNotificationError(),
  );
}

export async function createNotificationStreamTicket() {
  return await requestClient.post<NotificationStreamTicket>(
    '/notifications/stream-ticket',
    undefined,
    withSilentNotificationError(),
  );
}

export function buildNotificationStreamUrl(ticket: string): string {
  const base = (import.meta.env.VITE_GLOB_API_URL || '/api').replace(/\/$/, '');
  return `${base}/notifications/stream?ticket=${encodeURIComponent(ticket)}`;
}
