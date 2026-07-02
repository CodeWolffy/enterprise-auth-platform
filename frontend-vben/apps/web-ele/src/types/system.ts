export interface NoticeView {
  id: number | string;
  noticeTitle: string;
  noticeContent: string;
  published?: boolean;
  publishTime?: string | null;
  createdBy?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  workflowStatus?: string | null;
}

export interface MailChannelPreset {
  code: string;
  host: string;
  port: number;
  protocol: string;
  useSsl: boolean;
  useStartTls: boolean;
}

export interface MailChannel {
  id: number;
  tenantId: string;
  provider: string;
  mailHost: string;
  mailPort: number;
  mailUsername: string;
  mailFrom: string;
  mailProtocol: string;
  useSsl: boolean;
  useStartTls: boolean;
  enabled: boolean;
  passwordConfigured: boolean;
  inherited: boolean;
  sourceTenantId: string;
  createdAt: string;
  updatedAt: string;
}

export interface MailChannelSaveRequest {
  provider?: string;
  mailHost: string;
  mailPort: number;
  mailUsername: string;
  mailPassword?: string;
  mailFrom: string;
  mailProtocol?: string;
  useSsl: boolean;
  useStartTls: boolean;
  enabled: boolean;
}

export interface CategoryOption {
  code: string;
  name: string;
  matchers: string[];
}

export interface CategoryAuditView {
  eventType: string;
  operator: string;
  occurredAt?: number | null;
  payloadJson: string;
}

export interface CategoryAnalysis {
  code: string;
  name: string;
  targetType: string;
  matchers: string[];
  referenceCount: number;
  sampleReferences: string[];
  recentAudits: CategoryAuditView[];
  trend: { date: string; count: number }[];
}

