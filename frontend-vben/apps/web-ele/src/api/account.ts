import { requestClient } from '#/api/request';

export interface AccountProfileResponse {
  id: number;
  tenantId: string;
  username: string;
  displayName?: string | null;
  mobile?: string | null;
  email?: string | null;
  avatarFileKey?: string | null;
  avatarUrl?: string | null;
  enabled: boolean;
  mustChangePassword: boolean;
  passwordUpdatedAt?: string | null;
  lastLoginAt?: string | null;
  lastLoginIp?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export async function fetchAccountProfile() {
  return await requestClient.get<AccountProfileResponse>('/account/profile', {
    headers: { isSwitchTenant: false },
  });
}

export async function updateAccountProfile(payload: {
  displayName?: string | null;
  mobile?: string | null;
  email?: string | null;
}) {
  return await requestClient.put<AccountProfileResponse>('/account/profile', payload, {
    headers: { isSwitchTenant: false },
  });
}

export async function uploadAccountAvatar(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return await requestClient.put<AccountProfileResponse>('/account/profile/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      isSwitchTenant: false,
    },
  });
}

export async function changeAccountPassword(payload: { oldPassword: string; newPassword: string }) {
  return await requestClient.post<AccountProfileResponse>('/account/password/change', payload, {
    headers: { isSwitchTenant: false },
  });
}
