import { http } from '../http'
import type { ApiResponse } from '@/types/api'
import type { FileMetadataView, FilePage, FileQueryParams, FileVisibility } from '@/types/file'

export async function queryFiles(params?: FileQueryParams) {
  const { data } = await http.get<ApiResponse<FilePage>>('/api/files', { params })
  return data.data
}

export async function uploadStorageFile(file: File, visibility: FileVisibility = 'OWNER') {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.post<ApiResponse<FileMetadataView>>('/api/files/upload', formData, {
    params: { visibility },
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data.data
}

export async function deleteStorageFile(fileKey: string) {
  await http.delete(`/api/files/${encodeURIComponent(fileKey)}`)
}

export async function downloadStorageFile(fileKey: string) {
  const response = await http.get(`/api/files/${encodeURIComponent(fileKey)}`, {
    responseType: 'blob',
  })
  return response.data as Blob
}