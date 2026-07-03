export type WorkflowDefinitionStatus = 'DRAFT' | 'DEPLOYED' | 'DISABLED' | string;
export type WorkflowInstanceStatus = 'RUNNING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'TERMINATED' | string;
export type WorkflowTaskStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'TRANSFERRED' | string;
export type WorkflowRejectStrategy = 'END' | 'PREVIOUS' | 'RESTART' | 'TO_STEP' | 'TO_STARTER' | string;

export interface WorkflowStepView {
  stepIndex: number;
  name: string;
  candidateUserIds: number[];
  candidateGroupCodes: string[];
  rejectStrategy?: WorkflowRejectStrategy;
  rejectTarget?: number | null;
}

export interface WorkflowStepInput {
  name: string;
  candidateUserIds: number[];
  candidateGroupCodes: string[];
  rejectStrategy?: WorkflowRejectStrategy;
  rejectTarget?: number | null;
}

export interface WorkflowDefinitionView {
  id: number;
  tenantId: string;
  definitionKey: string;
  definitionName: string;
  version: number;
  status: WorkflowDefinitionStatus;
  steps: WorkflowStepView[];
  remark?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface WorkflowDefinitionRequest {
  definitionKey: string;
  definitionName: string;
  steps: WorkflowStepInput[];
  remark?: string;
}

export interface WorkflowDefinitionQueryParams {
  status?: string;
  page?: number;
  size?: number;
}

export interface WorkflowStartRequest {
  definitionKey: string;
  businessKey: string;
  title: string;
  variables?: Record<string, unknown>;
}

export interface WorkflowInstanceView {
  id: number;
  tenantId: string;
  definitionId: number;
  definitionKey: string;
  definitionVersion: number;
  businessKey: string;
  title: string;
  status: WorkflowInstanceStatus;
  starterUserId: number;
  starterUsername: string;
  currentStepIndex: number;
  variablesSnapshot: Record<string, unknown>;
  startedAt?: string | null;
  endedAt?: string | null;
}

export interface WorkflowInstanceQueryParams {
  status?: string;
  page?: number;
  size?: number;
}

export interface WorkflowTaskView {
  id: number;
  tenantId: string;
  instanceId: number;
  definitionId: number;
  stepIndex: number;
  stepName: string;
  status: WorkflowTaskStatus;
  candidateUserIds: number[];
  candidateGroupCodes: string[];
  assigneeUserId?: number | null;
  assigneeUsername?: string | null;
  comment?: string | null;
  createdAt?: string | null;
  completedAt?: string | null;
  actionable: boolean;
  urgeCount: number;
}

export interface WorkflowTaskTransferRequest {
  targetUserId: number;
  comment?: string;
}

export interface WorkflowTaskQueryParams {
  page?: number;
  size?: number;
  taskId?: number;
}

export interface WorkflowStartResult {
  instance: WorkflowInstanceView;
  currentTask?: WorkflowTaskView | null;
}

export interface WorkflowActionResult {
  instance: WorkflowInstanceView;
  nextTask?: WorkflowTaskView | null;
}

export interface WorkflowTaskUrgeView {
  id: number;
  taskId: number;
  instanceId: number;
  urgedByUserId: number;
  urgedByUsername: string;
  comment?: string | null;
  urgedAt?: string | null;
  targetUsernames: string[];
}

export interface WorkflowTaskUrgeResult {
  urge: WorkflowTaskUrgeView;
  totalUrgeCount: number;
  instance?: WorkflowInstanceView | null;
}
