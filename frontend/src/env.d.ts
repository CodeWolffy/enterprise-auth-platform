/// <reference types="vite/client" />

import type { DefineComponent } from 'vue'

declare module '*.vue' {
  const component: DefineComponent<Record<string, never>, Record<string, never>, any>
  export default component
}

declare module 'axios' {
  export interface AxiosRequestConfig {
    requestKey?: string
    retry?: number
    retryDelay?: number
    silentAuthFailure?: boolean
    suppressErrorMessage?: boolean
  }
}

interface ImportMetaEnv {
  readonly VITE_BACKEND_ORIGIN?: string
  readonly VITE_PUBLIC_CLIENT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
