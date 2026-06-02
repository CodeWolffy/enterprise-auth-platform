import { http } from '../http'
import type { ApiResponse } from '@/types/api'

export interface AccountProfileResponse {
  id: number
  tenantId: string
  username: string
  displayName?: string | null
  mobile?: string | null
  email?: string | null
  mustChangePassword: boolean
  passwordUpdatedAt?: string | null
}

export async function fetchAccountProfile() {
  const { data } = await http.get<ApiResponse<AccountProfileResponse>>('/api/account/profile')
  return data.data
}

export async function changeAccountPassword(payload: { oldPassword: string; newPassword: string }) {
  const { data } = await http.post<ApiResponse<AccountProfileResponse>>('/api/account/password/change', payload)
  return data.data
}