import type { PageResult } from './api'

export interface CodegenDataSourceView {
  id: number
  name: string
  jdbcUrl: string
  username?: string | null
  dbName?: string | null
  host?: string | null
  port?: number | null
  enabled: boolean
  external: boolean
  externalAuthorized: boolean
  authorizedAt?: number | null
  authorizationNote?: string | null
  createdAt?: number | null
  updatedAt?: number | null
}

export interface CodegenDataSourceRequest {
  name: string
  jdbcUrl: string
  username?: string | null
  password?: string | null
  dbName?: string | null
  host?: string | null
  port?: number | null
  enabled?: boolean
}

export interface CodegenConnectionTestResult {
  dataSourceId: number
  success: boolean
  message: string
}

export interface CodegenImportTableRequest {
  dataSourceId: number
  tableNames: string[]
  packageName?: string | null
  author?: string | null
}

export interface CodegenImportedTableView {
  id: number
  dataSourceId: number
  tableName: string
  tableComment?: string | null
  className?: string | null
  packageName?: string | null
  moduleName?: string | null
  businessName?: string | null
  functionName?: string | null
  functionAuthor?: string | null
  columnCount?: number | null
  updatedAt?: number | null
}

export interface CodegenColumnConfigView {
  id?: number | null
  columnName: string
  columnComment?: string | null
  columnType?: string | null
  dataType?: string | null
  javaType?: string | null
  javaField?: string | null
  primaryKey: boolean
  required: boolean
  insert: boolean
  edit: boolean
  list: boolean
  query: boolean
  queryType?: string | null
  htmlType?: string | null
  dictType?: string | null
  sort?: number | null
}

export interface CodegenTableConfigDetailView {
  table: CodegenImportedTableView
  columns: CodegenColumnConfigView[]
}

export type CodegenImportedTablePage = PageResult<CodegenImportedTableView>

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
  selectedFiles?: string[]
  autoRegister?: boolean
}

export interface CodegenGenerateResult {
  tableName: string
  moduleName: string
  outputRoot: string
  files: string[]
  registeredResourceKeys?: string[]
}

export interface CodegenRequest {
  tableName: string
  moduleName?: string
  packageName?: string
  className?: string
  includeBackend?: boolean
  includeFrontend?: boolean
  overwrite?: boolean
  selectedFiles?: string[]
  autoRegister?: boolean
}

export interface CodegenTemplateView {
  id?: number | null
  name: string
  language: 'java' | 'typescript' | 'vue'
  templateCategory: 'backend' | 'frontend' | 'api' | 'type' | 'view'
  pathPattern: string
  content: string
  description?: string | null
  builtin?: boolean
  createdAt?: number | null
  updatedAt?: number | null
}

export type CodegenTablePage = PageResult<CodegenTableView>
export type CodegenTemplatePage = PageResult<CodegenTemplateView>