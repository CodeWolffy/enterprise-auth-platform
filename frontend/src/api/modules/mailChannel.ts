import { http } from '../http'
import type { ApiResponse } from '@/types/api'
import type { MailChannel, MailChannelPreset, MailChannelSaveRequest } from '@/types/mailChannel'

export async function queryPresets() {
  const { data } = await http.get<ApiResponse<MailChannelPreset[]>>('/api/system/mail-channel/presets')
  return data.data
}

export async function queryMailChannel() {
  const { data } = await http.get<ApiResponse<MailChannel | null>>('/api/system/mail-channel')
  return data.data
}

export async function saveMailChannel(payload: MailChannelSaveRequest) {
  const { data } = await http.post<ApiResponse<MailChannel>>('/api/system/mail-channel', payload)
  return data.data
}

export async function deleteMailChannel() {
  await http.delete('/api/system/mail-channel')
}

export async function testSendMail(toEmail: string) {
  const { data } = await http.post<ApiResponse<{ success: boolean; message: string }>>(
    '/api/system/mail-channel/test',
    null,
    { params: { toEmail } },
  )
  return data.data
}