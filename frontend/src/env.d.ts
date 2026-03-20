/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_BACKEND_ORIGIN?: string
  readonly VITE_PUBLIC_CLIENT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
