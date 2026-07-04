import { requestClient } from '#/api/request';

export interface AccountProfileResponse {
  id: number;
  tenantId: string;
  username: string;
  displayName?: null | string;
  mobile?: null | string;
  email?: null | string;
  avatarFileKey?: null | string;
  avatarUrl?: null | string;
  enabled: boolean;
  mustChangePassword: boolean;
  passwordUpdatedAt?: null | string;
  lastLoginAt?: null | string;
  lastLoginIp?: null | string;
  createdAt?: null | string;
  updatedAt?: null | string;
}

export async function fetchAccountProfile() {
  return await requestClient.get<AccountProfileResponse>('/account/profile', {
    headers: { isSwitchTenant: false },
  });
}

export async function updateAccountProfile(payload: {
  displayName?: null | string;
  email?: null | string;
  mobile?: null | string;
}) {
  return await requestClient.put<AccountProfileResponse>(
    '/account/profile',
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function uploadAccountAvatar(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return await requestClient.put<AccountProfileResponse>(
    '/account/profile/avatar',
    formData,
    {
      headers: {
        'Content-Type': 'multipart/form-data',
        isSwitchTenant: false,
      },
    },
  );
}

export async function changeAccountPassword(payload: {
  newPassword: string;
  oldPassword: string;
}) {
  return await requestClient.post<AccountProfileResponse>(
    '/account/password/change',
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}
