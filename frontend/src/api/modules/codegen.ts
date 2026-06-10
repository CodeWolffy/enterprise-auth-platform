import { http } from '../http'
import type { ApiResponse } from '@/types/api'
import type {
  CodegenDataSourceRequest,
  CodegenDataSourceView,
  CodegenConnectionTestResult,
  CodegenGenerateResult,
  CodegenImportTableRequest,
  CodegenImportedTablePage,
  CodegenImportedTableView,
  CodegenPreviewResult,
  CodegenRequest,
  CodegenTableConfigDetailView,
  CodegenTableDetailView,
  CodegenTablePage,
  CodegenTemplatePage,
  CodegenTemplateView,
} from '@/types/codegen'

export async function queryCodegenDataSources() {
  const { data } = await http.get<ApiResponse<CodegenDataSourceView[]>>('/api/codegen/datasources')
  return data.data
}

export async function createCodegenDataSource(payload: CodegenDataSourceRequest) {
  const { data } = await http.post<ApiResponse<CodegenDataSourceView>>('/api/codegen/datasources', payload)
  return data.data
}

export async function updateCodegenDataSource(id: number, payload: CodegenDataSourceRequest) {
  const { data } = await http.put<ApiResponse<CodegenDataSourceView>>(`/api/codegen/datasources/${id}`, payload)
  return data.data
}

export async function deleteCodegenDataSource(id: number) {
  await http.delete(`/api/codegen/datasources/${id}`)
}

export async function authorizeCodegenDataSource(id: number, note?: string) {
  const { data } = await http.post<ApiResponse<CodegenDataSourceView>>(`/api/codegen/datasources/${id}/authorize`, {
    note: note || null,
  })
  return data.data
}

export async function testCodegenDataSource(id: number) {
  const { data } = await http.post<ApiResponse<CodegenConnectionTestResult>>(`/api/codegen/datasources/${id}/test`)
  return data.data
}

export async function queryCodegenDataSourceTables(id: number, params?: { keyword?: string; page?: number; size?: number }) {
  const { data } = await http.get<ApiResponse<CodegenTablePage>>(`/api/codegen/datasources/${id}/tables`, { params })
  return data.data
}

export async function importCodegenTables(payload: CodegenImportTableRequest) {
  const { data } = await http.post<ApiResponse<CodegenImportedTableView[]>>('/api/codegen/tables/import', payload)
  return data.data
}

export async function queryCodegenImportedTables(params?: { keyword?: string; page?: number; size?: number }) {
  const { data } = await http.get<ApiResponse<CodegenImportedTablePage>>('/api/codegen/imported-tables', { params })
  return data.data
}

export async function getCodegenImportedTable(id: number) {
  const { data } = await http.get<ApiResponse<CodegenTableConfigDetailView>>(`/api/codegen/imported-tables/${id}`)
  return data.data
}

export async function updateCodegenImportedTableColumns(id: number, columns: CodegenTableConfigDetailView['columns']) {
  const { data } = await http.put<ApiResponse<CodegenTableConfigDetailView>>(`/api/codegen/imported-tables/${id}/columns`, { columns })
  return data.data
}

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

export async function queryCodegenTemplates(params?: { keyword?: string; templateCategory?: string; page?: number; size?: number }) {
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