import type {
  WorkflowActionResult,
  WorkflowDefinitionQueryParams,
  WorkflowDefinitionRequest,
  WorkflowDefinitionView,
  WorkflowInstanceQueryParams,
  WorkflowInstanceView,
  WorkflowStartRequest,
  WorkflowStartResult,
  WorkflowTaskQueryParams,
  WorkflowTaskTransferRequest,
  WorkflowTaskUrgeResult,
  WorkflowTaskUrgeView,
  WorkflowTaskView,
} from '#/types/workflow';

import { requestClient } from '#/api/request';

export async function queryWorkflowDefinitions(
  params?: WorkflowDefinitionQueryParams,
) {
  return await requestClient.get<{
    page: number;
    records: WorkflowDefinitionView[];
    size: number;
    total: number;
  }>('/workflow/process-definitions', {
    params,
    headers: { isSwitchTenant: false },
  });
}

export async function createWorkflowDefinition(
  payload: WorkflowDefinitionRequest,
) {
  return await requestClient.post<WorkflowDefinitionView>(
    '/workflow/process-definitions',
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function deployWorkflowDefinition(definitionId: number) {
  return await requestClient.put<WorkflowDefinitionView>(
    `/workflow/process-definitions/${definitionId}/deploy`,
    null,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function disableWorkflowDefinition(definitionId: number) {
  return await requestClient.put<WorkflowDefinitionView>(
    `/workflow/process-definitions/${definitionId}/disable`,
    null,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function queryMyWorkflowInstances(
  params?: WorkflowInstanceQueryParams,
) {
  return await requestClient.get<{
    page: number;
    records: WorkflowInstanceView[];
    size: number;
    total: number;
  }>('/workflow/instances/my', { params, headers: { isSwitchTenant: false } });
}

export async function startWorkflowInstance(payload: WorkflowStartRequest) {
  return await requestClient.post<WorkflowStartResult>(
    '/workflow/instances',
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function withdrawWorkflowInstance(instanceId: number) {
  return await requestClient.put<WorkflowInstanceView>(
    `/workflow/instances/${instanceId}/withdraw`,
    null,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function terminateWorkflowInstance(
  instanceId: number,
  comment?: string,
) {
  return await requestClient.put<WorkflowInstanceView>(
    `/workflow/instances/${instanceId}/terminate`,
    { comment },
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function queryWorkflowTodoTasks(params?: WorkflowTaskQueryParams) {
  return await requestClient.get<{
    page: number;
    records: WorkflowTaskView[];
    size: number;
    total: number;
  }>('/workflow/tasks/todo', { params, headers: { isSwitchTenant: false } });
}

export async function queryWorkflowDoneTasks(params?: WorkflowTaskQueryParams) {
  return await requestClient.get<{
    page: number;
    records: WorkflowTaskView[];
    size: number;
    total: number;
  }>('/workflow/tasks/done', { params, headers: { isSwitchTenant: false } });
}

export async function approveWorkflowTask(taskId: number, comment?: string) {
  return await requestClient.put<WorkflowActionResult>(
    `/workflow/tasks/${taskId}/approve`,
    { comment },
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function rejectWorkflowTask(taskId: number, comment?: string) {
  return await requestClient.put<WorkflowActionResult>(
    `/workflow/tasks/${taskId}/reject`,
    { comment },
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function transferWorkflowTask(
  taskId: number,
  payload: WorkflowTaskTransferRequest,
) {
  return await requestClient.put<WorkflowActionResult>(
    `/workflow/tasks/${taskId}/transfer`,
    payload,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function urgeWorkflowTask(taskId: number, comment?: string) {
  return await requestClient.put<WorkflowTaskUrgeResult>(
    `/workflow/tasks/${taskId}/urge`,
    { comment },
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function listWorkflowTaskUrges(taskId: number) {
  return await requestClient.get<WorkflowTaskUrgeView[]>(
    `/workflow/tasks/${taskId}/urges`,
    {
      headers: { isSwitchTenant: false },
    },
  );
}

export async function listWorkflowInstanceUrges(
  instanceId: number,
  page = 1,
  size = 20,
) {
  return await requestClient.get<{
    page: number;
    records: WorkflowTaskUrgeView[];
    size: number;
    total: number;
  }>(`/workflow/instances/${instanceId}/urges`, {
    params: { page, size },
    headers: { isSwitchTenant: false },
  });
}
