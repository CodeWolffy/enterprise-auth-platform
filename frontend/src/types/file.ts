export type FileVisibility = 'PUBLIC' | 'TENANT' | 'OWNER' | 'PRIVATE'

export interface FileMetadataView {
  fileKey: string
  originalName: string
  contentType: string
  size: number
  storageType: string
  visibility: FileVisibility | string
  tenantId: string
  ownerUserId?: number | null
  createdAt?: number | null
  url?: string | null
}

export interface FileQueryParams {
  keyword?: string
  contentType?: string
  storageType?: string
  visibility?: string
  page?: number
  size?: number
}

export interface FilePage {
  total: number
  page: number
  size: number
  records: FileMetadataView[]
}