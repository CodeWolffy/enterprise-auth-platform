import { http } from '../http'
import type { ApiResponse, PageResult } from '@/types/api'
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
  WorkflowTaskView,
} from '@/types/workflow'

export async function queryWorkflowDefinitions(params?: WorkflowDefinitionQueryParams) {
  const { data } = await http.get<ApiResponse<PageResult<WorkflowDefinitionView>>>(
    '/api/workflow/process-definitions',
    { params },
  )
  return data.data
}

export async function createWorkflowDefinition(payload: WorkflowDefinitionRequest) {
  const { data } = await http.post<ApiResponse<WorkflowDefinitionView>>('/api/workflow/process-definitions', payload)
  return data.data
}

export async function deployWorkflowDefinition(definitionId: number) {
  const { data } = await http.put<ApiResponse<WorkflowDefinitionView>>(
    `/api/workflow/process-definitions/${definitionId}/deploy`,
  )
  return data.data
}

export async function disableWorkflowDefinition(definitionId: number) {
  const { data } = await http.put<ApiResponse<WorkflowDefinitionView>>(
    `/api/workflow/process-definitions/${definitionId}/disable`,
  )
  return data.data
}

export async function startWorkflowInstance(payload: WorkflowStartRequest) {
  const { data } = await http.post<ApiResponse<WorkflowStartResult>>('/api/workflow/instances', payload)
  return data.data
}

export async function queryMyWorkflowInstances(params?: WorkflowInstanceQueryParams) {
  const { data } = await http.get<ApiResponse<PageResult<WorkflowInstanceView>>>('/api/workflow/instances/my', { params })
  return data.data
}

export async function withdrawWorkflowInstance(instanceId: number) {
  const { data } = await http.put<ApiResponse<WorkflowInstanceView>>(`/api/workflow/instances/${instanceId}/withdraw`)
  return data.data
}

export async function terminateWorkflowInstance(instanceId: number, comment?: string) {
  const { data } = await http.put<ApiResponse<WorkflowInstanceView>>(`/api/workflow/instances/${instanceId}/terminate`, { comment })
  return data.data
}

export async function queryWorkflowTodoTasks(params?: WorkflowTaskQueryParams) {
  const { data } = await http.get<ApiResponse<PageResult<WorkflowTaskView>>>('/api/workflow/tasks/todo', { params })
  return data.data
}

export async function queryWorkflowDoneTasks(params?: WorkflowTaskQueryParams) {
  const { data } = await http.get<ApiResponse<PageResult<WorkflowTaskView>>>('/api/workflow/tasks/done', { params })
  return data.data
}

export async function approveWorkflowTask(taskId: number, comment?: string) {
  const { data } = await http.put<ApiResponse<WorkflowActionResult>>(`/api/workflow/tasks/${taskId}/approve`, { comment })
  return data.data
}

export async function rejectWorkflowTask(taskId: number, comment?: string) {
  const { data } = await http.put<ApiResponse<WorkflowActionResult>>(`/api/workflow/tasks/${taskId}/reject`, { comment })
  return data.data
}

export async function transferWorkflowTask(taskId: number, payload: WorkflowTaskTransferRequest) {
  const { data } = await http.put<ApiResponse<WorkflowActionResult>>(`/api/workflow/tasks/${taskId}/transfer`, payload)
  return data.data
}