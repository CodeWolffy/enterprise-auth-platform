export type LogTagType = 'danger' | 'info' | 'success' | 'warning';

export interface LogStatusMeta {
  label: string;
  type: LogTagType;
}

const OPERATION_STATUS: Record<string, LogStatusMeta> = {
  '0': { label: '失败', type: 'danger' },
  '1': { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  SUCCESS: { label: '成功', type: 'success' },
};

const LOGIN_STATUS: Record<string, LogStatusMeta> = {
  FAILED: { label: '失败', type: 'danger' },
  LOCKED: { label: '锁定', type: 'warning' },
  SUCCESS: { label: '成功', type: 'success' },
};

function fallbackStatus(status?: null | string): LogStatusMeta {
  return { label: status || '-', type: 'info' };
}

export function operationStatusMeta(status?: null | string): LogStatusMeta {
  return status
    ? (OPERATION_STATUS[status] ?? fallbackStatus(status))
    : fallbackStatus(status);
}

export function loginStatusMeta(status?: null | string): LogStatusMeta {
  return status
    ? (LOGIN_STATUS[status] ?? fallbackStatus(status))
    : fallbackStatus(status);
}
