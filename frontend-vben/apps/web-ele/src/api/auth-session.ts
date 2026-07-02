import type { SessionPageResult, UserSessionView } from '#/types/auth-models';

import { requestClient } from '#/api/request';

export async function querySessions(
  scope: 'all' | 'own' = 'own',
  page?: number,
  size?: number,
) {
  return await requestClient.get<SessionPageResult | UserSessionView[]>(
    '/auth/sessions',
    {
      params: { scope, page, size },
      headers: { isSwitchTenant: false },
    },
  );
}

export async function forceOffline(sessionId: string) {
  return await requestClient.post<any>(
    `/auth/sessions/${sessionId}/offline`,
    {},
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function fetchRegisterOptions() {
  return await requestClient.get<{
    defaultRoleCodes: string[];
    defaultTenantId: string;
  }>('/auth/register/options', {
    headers: { isSwitchTenant: false },
  });
}

export interface RegisterPayload {
  username: string;
  displayName: string;
  password: string;
  mobile?: string;
  email?: string;
  captchaId: string;
}

export async function registerApi(payload: RegisterPayload) {
  return await requestClient.post<any>('/auth/register', payload, {
    headers: { isSwitchTenant: false, isToken: false },
  });
}

export async function requestPasswordReset(payload: {
  captchaId: string;
  email: string;
  username: string;
}) {
  return await requestClient.post<any>(
    '/auth/password/reset/request',
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function verifyPasswordResetToken(token: string) {
  return await requestClient.post<any>(
    '/auth/password/reset/verify',
    { token },
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function confirmPasswordReset(payload: {
  newPassword: string;
  token: string;
}) {
  return await requestClient.post<any>(
    '/auth/password/reset/confirm',
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}
