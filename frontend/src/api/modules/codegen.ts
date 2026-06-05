import { http } from '../http'
import type { ApiResponse } from '@/types/api'
import type {
  CodegenGenerateResult,
  CodegenPreviewResult,
  CodegenRequest,
  CodegenTableDetailView,
  CodegenTablePage,
  CodegenTemplatePage,
  CodegenTemplateView,
} from '@/types/codegen'

export async function queryCodegenTables(params?: { keyword?: string; page?: number; size?: number }) {
  const { data } = await http.get<ApiResponse<CodegenTablePage>>('/api/codegen/tables', { params })
  return data.data
}

export async function getCodegenTable(tableName: string) {
  const { data } = await http.get<ApiResponse<CodegenTableDetailView>>(`/api/codegen/tables/${encodeURIComponent(tableName)}`)
  return data.data
}

export async function previewCodegen(payload: CodegenRequest) {
  const { data } = await http.post<ApiResponse<CodegenPreviewResult>>('/api/codegen/preview', payload)
  return data.data
}

export async function generateCodegen(payload: CodegenRequest) {
  const { data } = await http.post<ApiResponse<CodegenGenerateResult>>('/api/codegen/generate', payload)
  return data.data
}

export async function downloadCodegen(payload: CodegenRequest) {
  const response = await http.post<Blob>('/api/codegen/download', payload, { responseType: 'blob' })
  return response.data
}

export async function queryCodegenTemplates(params?: { keyword?: string; page?: number; size?: number }) {
  const { data } = await http.get<ApiResponse<CodegenTemplatePage>>('/api/codegen/templates', { params })
  return data.data
}

export async function getCodegenTemplate(id: number) {
  const { data } = await http.get<ApiResponse<CodegenTemplateView>>(`/api/codegen/templates/${id}`)
  return data.data
}

export async function createCodegenTemplate(payload: CodegenTemplateView) {
  const { data } = await http.post<ApiResponse<CodegenTemplateView>>('/api/codegen/templates', payload)
  return data.data
}

export async function updateCodegenTemplate(id: number, payload: CodegenTemplateView) {
  const { data } = await http.put<ApiResponse<CodegenTemplateView>>(`/api/codegen/templates/${id}`, payload)
  return data.data
}

export async function deleteCodegenTemplate(id: number) {
  await http.delete(`/api/codegen/templates/${id}`)
}