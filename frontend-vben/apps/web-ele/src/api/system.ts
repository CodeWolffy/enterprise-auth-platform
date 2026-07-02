import { requestClient } from '#/api/request';
import type { FeatureFlags } from '#/types/api';
import type { NoticeView } from '#/types/system';

export interface SecurityPasswordPolicy {
  passwordMinLength: number;
  passwordMaxLength: number;
  passwordRequireLetter: boolean;
  passwordRequireNumber: boolean;
  passwordRequireSpecial: boolean;
}

export async function queryPasswordPolicy() {
  return await requestClient.get<SecurityPasswordPolicy>('/security/policy/password-policy', {
    headers: { isSwitchTenant: false },
  });
}

export async function updatePasswordPolicy(data: SecurityPasswordPolicy) {
  return await requestClient.put('/security/policy/password-policy', data, {
    headers: { isSwitchTenant: false },
  });
}

export async function queryFeatures() {
  return await requestClient.get<FeatureFlags>('/system/features', {
    headers: { isSwitchTenant: false },
  });
}

export async function getPublishedNotice(id: string | number) {
  return await requestClient.get<NoticeView>(`/system/notices/${id}/published`, {
    headers: { isSwitchTenant: false },
  });
}
