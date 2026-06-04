import type { PageResult } from './api'

export interface CodegenTableView {
  tableName: string
  tableComment?: string | null
  engine?: string | null
  tableRows?: number | null
  dataLength?: number | null
  indexLength?: number | null
  createdAt?: number | null
  updatedAt?: number | null
}

export interface CodegenColumnView {
  columnName: string
  dataType: string
  columnType: string
  nullable: boolean
  primaryKey: boolean
  autoIncrement: boolean
  columnDefault?: string | null
  columnComment?: string | null
  javaType: string
  javaField: string
  tsType: string
}

export interface CodegenTableDetailView {
  table: CodegenTableView
  columns: CodegenColumnView[]
}

export interface CodegenFilePreview {
  path: string
  language: string
  content: string
}

export interface CodegenPreviewResult {
  tableName: string
  moduleName: string
  className: string
  generatedRoot: string
  files: CodegenFilePreview[]
}

export interface CodegenGenerateResult {
  tableName: string
  moduleName: string
  outputRoot: string
  files: string[]
}

export interface CodegenRequest {
  tableName: string
  moduleName?: string
  packageName?: string
  className?: string
  includeBackend?: boolean
  includeFrontend?: boolean
  overwrite?: boolean
}

export type CodegenTablePage = PageResult<CodegenTableView>