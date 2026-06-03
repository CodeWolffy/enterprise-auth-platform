export interface MailChannelPreset {
  code: string
  host: string
  port: number
  protocol: string
  useSsl: boolean
  useStartTls: boolean
}

export interface MailChannel {
  id: number
  tenantId: string
  provider: string
  mailHost: string
  mailPort: number
  mailUsername: string
  mailFrom: string
  mailProtocol: string
  useSsl: boolean
  useStartTls: boolean
  enabled: boolean
  passwordConfigured: boolean
  inherited: boolean
  sourceTenantId: string
  createdAt: string
  updatedAt: string
}

export interface MailChannelSaveRequest {
  provider?: string
  mailHost: string
  mailPort: number
  mailUsername: string
  mailPassword?: string
  mailFrom: string
  mailProtocol?: string
  useSsl: boolean
  useStartTls: boolean
  enabled: boolean
}