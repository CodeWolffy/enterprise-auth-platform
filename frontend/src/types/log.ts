export interface LogPage<T = unknown> {
  total: number
  page: number
  size: number
  records: T[]
}

export interface OperationLogRecord {
  id: number
  tenantId: string
  eventType: string
  operator: string
  payloadJson?: string
  requestId?: string
  clientIp?: string
  location?: string
  method?: string
  requestUri?: string
  requestParams?: string
  requestTime?: number
  status?: string
  exMsg?: string
  createdAt: string
}

export interface LoginLogRecord {
  id: number
  tenantId: string
  userName: string
  ipAddr: string
  location?: string
  status: string
  msg?: string
  browser?: string
  os?: string
  createdAt: string
}