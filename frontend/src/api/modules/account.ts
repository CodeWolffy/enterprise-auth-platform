import { http } from '../http'
import type { ApiResponse } from '@/types/api'

export interface AccountProfileResponse {
  id: number
  tenantId: string
  username: string
  displayName?: string | null
  mobile?: string | null
  email?: string | null
  avatarFileKey?: string | null
  avatarUrl?: string | null
  enabled: boolean
  mustChangePassword: boolean
  passwordUpdatedAt?: string | null
  lastLoginAt?: string | null
  lastLoginIp?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export async function fetchAccountProfile() {
  const { data } = await http.get<ApiResponse<AccountProfileResponse>>('/api/account/profile')
  return data.data
}

export async function updateAccountProfile(payload: AccountProfileUpdatePayload) {
  const { data } = await http.put<ApiResponse<AccountProfileResponse>>('/api/account/profile', payload)
  return data.data
}

export async function uploadAccountAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.put<ApiResponse<AccountProfileResponse>>('/api/account/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data.data
}

export async function changeAccountPassword(payload: { oldPassword: string; newPassword: string }) {
  const { data } = await http.post<ApiResponse<AccountProfileResponse>>('/api/account/password/change', payload)
  return data.data
}
