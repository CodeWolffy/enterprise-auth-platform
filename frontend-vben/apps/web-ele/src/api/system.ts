import type { ScopedRequestConfig } from '#/api/request';
import type { FeatureFlags } from '#/types/api';
import type { NoticeView } from '#/types/system';

import { requestClient } from '#/api/request';

function withLocalErrorHandling(
  config: ScopedRequestConfig = {},
): ScopedRequestConfig {
  return { ...config, suppressErrorMessage: true };
}

export interface SecurityPolicy {
  passwordMinLength: number;
  passwordMaxLength: number;
  passwordRequireLetter: boolean;
  passwordRequireNumber: boolean;
  passwordRequireSpecial: boolean;
  passwordHistoryCount: number;
  passwordExpireDays: number;
  loginFailureMaxAttempts: number;
  loginFailureLockMinutes: number;
  loginFailureWindowMinutes: number;
  captchaEnabled: boolean;
}

export async function querySecurityPolicy() {
  return await requestClient.get<SecurityPolicy>(
    '/security/policy',
    withLocalErrorHandling(),
  );
}

export async function updateSecurityPolicy(data: SecurityPolicy) {
  return await requestClient.put<SecurityPolicy>(
    '/security/policy',
    data,
    withLocalErrorHandling(),
  );
}

export async function queryPlatformSecurityPolicy() {
  return await requestClient.get<SecurityPolicy>(
    '/security/policy/platform',
    withLocalErrorHandling({ headers: { isSwitchTenant: false } }),
  );
}

export async function updatePlatformSecurityPolicy(data: SecurityPolicy) {
  return await requestClient.put<SecurityPolicy>(
    '/security/policy/platform',
    data,
    withLocalErrorHandling({ headers: { isSwitchTenant: false } }),
  );
}

export async function queryFeatures() {
  return await requestClient.get<FeatureFlags>('/system/features', {
    headers: { isSwitchTenant: false },
  });
}

export async function getPublishedNotice(id: number | string) {
  return await requestClient.get<NoticeView>(
    `/system/notices/${id}/published`,
    {
      headers: { isSwitchTenant: false },
    },
  );
}
