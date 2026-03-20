import { http } from './http'
import type { ApiResponse, ConfigView, DictView, FeatureFlags, NoticeView } from '@/types/auth'

export async function queryFeatures() {
  const { data } = await http.get<ApiResponse<FeatureFlags>>('/api/system/features')
  return data.data
}

export async function queryDicts() {
  const { data } = await http.get<ApiResponse<DictView[]>>('/api/system/dicts')
  return data.data
}

export async function createDict(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<DictView>>('/api/system/dicts', payload)
  return data.data
}

export async function updateDict(id: number, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<DictView>>(`/api/system/dicts/${id}`, payload)
  return data.data
}

export async function deleteDict(id: number) {
  await http.delete(`/api/system/dicts/${id}`)
}

export async function queryConfigs() {
  const { data } = await http.get<ApiResponse<ConfigView[]>>('/api/system/configs')
  return data.data
}

export async function createConfig(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<ConfigView>>('/api/system/configs', payload)
  return data.data
}

export async function updateConfig(id: number, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<ConfigView>>(`/api/system/configs/${id}`, payload)
  return data.data
}

export async function deleteConfig(id: number) {
  await http.delete(`/api/system/configs/${id}`)
}

export async function queryNotices() {
  const { data } = await http.get<ApiResponse<NoticeView[]>>('/api/system/notices')
  return data.data
}

export async function createNotice(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<NoticeView>>('/api/system/notices', payload)
  return data.data
}

export async function updateNotice(id: number, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<NoticeView>>(`/api/system/notices/${id}`, payload)
  return data.data
}

export async function deleteNotice(id: number) {
  await http.delete(`/api/system/notices/${id}`)
}
