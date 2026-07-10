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

export interface PasswordResetPolicy {
  passwordMaxLength: number;
  passwordMinLength: number;
  passwordRequireLetter: boolean;
  passwordRequireNumber: boolean;
  passwordRequireSpecial: boolean;
}

export interface PasswordResetRequestPayload {
  captchaId: string;
  email: string;
  tenantId?: string;
  username: string;
}

export interface PasswordResetRequestResponse {
  message: string;
  result: 'EMAIL_SENT';
}

export interface PasswordResetVerifyResponse {
  passwordPolicy: null | PasswordResetPolicy;
  username: null | string;
  valid: boolean;
}

export interface PasswordResetConfirmResponse {
  message: string;
}

export async function registerApi(payload: RegisterPayload) {
  return await requestClient.post<any>('/auth/register', payload, {
    headers: { isSwitchTenant: false, isToken: false },
  });
}

export async function requestPasswordReset(
  payload: PasswordResetRequestPayload,
) {
  return await requestClient.post<PasswordResetRequestResponse>(
    '/auth/password/reset/request',
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function verifyPasswordResetToken(token: string) {
  return await requestClient.post<PasswordResetVerifyResponse>(
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
  return await requestClient.post<PasswordResetConfirmResponse>(
    '/auth/password/reset/confirm',
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}
