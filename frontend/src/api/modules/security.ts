import { http } from '../http'
import type { ApiResponse } from '@/types/api'

export interface SecurityPasswordPolicy {
  passwordMinLength: number
  passwordMaxLength: number
  passwordRequireLetter: boolean
  passwordRequireNumber: boolean
  passwordRequireSpecial: boolean
}

export async function queryPasswordPolicy() {
  const { data } = await http.get<ApiResponse<SecurityPasswordPolicy>>('/api/security/policy/password-policy')
  return data.data
}
