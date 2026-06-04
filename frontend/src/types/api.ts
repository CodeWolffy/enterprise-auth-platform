/** 统一 API 响应与基础设施类型 */

export interface ApiResponse<T> {
  code: string
  success: boolean
  data: T
  message: string
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export interface FeatureFlags {
  gatewayEnabled: boolean
  nacosEnabled: boolean
  mqEnabled: boolean
  seataEnabled: boolean
  jobEnabled: boolean
  lokiEnabled: boolean
}