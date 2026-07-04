export type WorkflowDefinitionStatus =
  | 'DEPLOYED'
  | 'DISABLED'
  | 'DRAFT'
  | string;
export type WorkflowInstanceStatus =
  | 'APPROVED'
  | 'REJECTED'
  | 'RUNNING'
  | 'TERMINATED'
  | 'WITHDRAWN'
  | string;
export type WorkflowTaskStatus =
  | 'APPROVED'
  | 'CANCELLED'
  | 'PENDING'
  | 'REJECTED'
  | 'TRANSFERRED'
  | string;
export type WorkflowRejectStrategy =
  | 'END'
  | 'PREVIOUS'
  | 'RESTART'
  | 'TO_STARTER'
  | 'TO_STEP'
  | string;

export interface WorkflowStepView {
  stepIndex: number;
  name: string;
  candidateUserIds: number[];
  candidateGroupCodes: string[];
  rejectStrategy?: WorkflowRejectStrategy;
  rejectTarget?: null | number;
}

export interface WorkflowStepInput {
  name: string;
  candidateUserIds: number[];
  candidateGroupCodes: string[];
  rejectStrategy?: WorkflowRejectStrategy;
  rejectTarget?: null | number;
}

export interface WorkflowDefinitionView {
  id: number;
  tenantId: string;
  definitionKey: string;
  definitionName: string;
  version: number;
  status: WorkflowDefinitionStatus;
  steps: WorkflowStepView[];
  remark?: null | string;
  createdAt?: null | string;
  updatedAt?: null | string;
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
  startedAt?: null | string;
  endedAt?: null | string;
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
  assigneeUserId?: null | number;
  assigneeUsername?: null | string;
  comment?: null | string;
  createdAt?: null | string;
  completedAt?: null | string;
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
  currentTask?: null | WorkflowTaskView;
}

export interface WorkflowActionResult {
  instance: WorkflowInstanceView;
  nextTask?: null | WorkflowTaskView;
}

export interface WorkflowTaskUrgeView {
  id: number;
  taskId: number;
  instanceId: number;
  urgedByUserId: number;
  urgedByUsername: string;
  comment?: null | string;
  urgedAt?: null | string;
  targetUsernames: string[];
}

export interface WorkflowTaskUrgeResult {
  urge: WorkflowTaskUrgeView;
  totalUrgeCount: number;
  instance?: null | WorkflowInstanceView;
}
