import { http } from '../http'
import type { ApiResponse } from '@/types/api'
import type {
  CodegenGenerateResult,
  CodegenPreviewResult,
  CodegenRequest,
  CodegenTableDetailView,
  CodegenTablePage,
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