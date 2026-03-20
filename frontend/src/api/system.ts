import { http } from './http'
import type { ApiResponse, CategoryAnalysis, CategoryOption, ConfigView, DictView, FeatureFlags, NoticeView } from '@/types/auth'

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export type SortDirection = 'asc' | 'desc'

export interface DictQueryParams {
  dictType?: string
  category?: string
  keyword?: string
  page?: number
  size?: number
  sortBy?: 'createdAt' | 'dictType' | 'dictCode'
  sortDirection?: SortDirection
}

export interface ConfigQueryParams {
  category?: string
  keyword?: string
  page?: number
  size?: number
  sortBy?: 'createdAt' | 'configKey' | 'configName'
  sortDirection?: SortDirection
}

export interface NoticeQueryParams {
  keyword?: string
  published?: boolean
  workflowStatus?: 'DRAFT' | 'SCHEDULED' | 'PUBLISHED'
  page?: number
  size?: number
  sortBy?: 'publishTime' | 'createdAt' | 'noticeTitle'
  sortDirection?: SortDirection
}

export async function queryFeatures() {
  const { data } = await http.get<ApiResponse<FeatureFlags>>('/api/system/features')
  return data.data
}

export async function queryCategories() {
  const { data } = await http.get<ApiResponse<Record<string, CategoryOption[]>>>('/api/system/categories')
  return data.data
}

export async function queryCategoryOptions(targetType: 'dict' | 'config') {
  const { data } = await http.get<ApiResponse<CategoryOption[]>>(`/api/system/categories/${targetType}`)
  return data.data
}

export async function queryCategoryAnalysis(targetType: 'dict' | 'config', code: string) {
  const { data } = await http.get<ApiResponse<CategoryAnalysis>>(`/api/system/categories/${targetType}/${code}/analysis`)
  return data.data
}

export async function createCategoryOption(targetType: 'dict' | 'config', payload: { code: string; name: string; matchers: string[] }) {
  const { data } = await http.post<ApiResponse<CategoryOption>>(`/api/system/categories/${targetType}`, payload)
  return data.data
}

export async function updateCategoryOption(
  targetType: 'dict' | 'config',
  code: string,
  payload: { code: string; name: string; matchers: string[] },
) {
  const { data } = await http.put<ApiResponse<CategoryOption>>(`/api/system/categories/${targetType}/${code}`, payload)
  return data.data
}

export async function deleteCategoryOption(targetType: 'dict' | 'config', code: string) {
  await http.delete(`/api/system/categories/${targetType}/${code}`)
}

export async function queryDicts(params?: DictQueryParams) {
  const { data } = await http.get<ApiResponse<PageResult<DictView>>>('/api/system/dicts', { params })
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

export async function queryConfigs(params?: ConfigQueryParams) {
  const { data } = await http.get<ApiResponse<PageResult<ConfigView>>>('/api/system/configs', { params })
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

export async function queryNotices(params?: NoticeQueryParams) {
  const { data } = await http.get<ApiResponse<PageResult<NoticeView>>>('/api/system/notices', { params })
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
