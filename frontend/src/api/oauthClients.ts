import { http } from './http'
import type { ApiResponse, ClientStatusPayload, ClientView, RotateClientSecretPayload } from '@/types/auth'

export async function queryClients() {
  const { data } = await http.get<ApiResponse<ClientView[]>>('/api/oauth-clients')
  return data.data
}

export async function queryClientDetail(id: number) {
  const { data } = await http.get<ApiResponse<ClientView>>(`/api/oauth-clients/${id}`)
  return data.data
}

export async function createClient(payload: Record<string, unknown>) {
  const { data } = await http.post<ApiResponse<ClientView>>('/api/oauth-clients', payload)
  return data.data
}

export async function updateClient(id: number, payload: Record<string, unknown>) {
  const { data } = await http.put<ApiResponse<ClientView>>(`/api/oauth-clients/${id}`, payload)
  return data.data
}

export async function updateClientStatus(id: number, payload: ClientStatusPayload) {
  const { data } = await http.put<ApiResponse<ClientView>>(`/api/oauth-clients/${id}/status`, payload)
  return data.data
}

export async function rotateClientSecret(id: number, payload: RotateClientSecretPayload) {
  const { data } = await http.post<ApiResponse<ClientView>>(`/api/oauth-clients/${id}/rotate-secret`, payload)
  return data.data
}

export async function deleteClient(id: number) {
  await http.delete(`/api/oauth-clients/${id}`)
}
