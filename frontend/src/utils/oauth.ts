const verifierKey = 'eap.oauth.verifier'
const stateKey = 'eap.oauth.state'
const tenantKey = 'eap.oauth.tenant'

function resolveBackendOrigin() {
  const configuredOrigin = import.meta.env.VITE_BACKEND_ORIGIN
  if (configuredOrigin) {
    return configuredOrigin
  }
  if (typeof window === 'undefined') {
    return 'http://127.0.0.1:8080'
  }
  return `${window.location.protocol}//${window.location.hostname}:8080`
}

const backendOrigin = resolveBackendOrigin()
const publicClientId = import.meta.env.VITE_PUBLIC_CLIENT_ID ?? 'eap-frontend-spa'

export function getBackendOrigin() {
  return backendOrigin
}

export function getPublicClientId() {
  return publicClientId
}

export function getRedirectUri() {
  return `${window.location.origin}/auth/callback`
}

export async function createOAuthRedirect(tenantId: string) {
  const state = randomString(24)
  const verifier = randomString(64)
  sessionStorage.setItem(verifierKey, verifier)
  sessionStorage.setItem(stateKey, state)
  sessionStorage.setItem(tenantKey, tenantId)
  const challenge = await createCodeChallenge(verifier)
  const url = new URL('/oauth2/authorize', backendOrigin)
  url.searchParams.set('response_type', 'code')
  url.searchParams.set('client_id', publicClientId)
  url.searchParams.set('redirect_uri', getRedirectUri())
  url.searchParams.set('scope', 'openid profile api.read api.write')
  url.searchParams.set('state', state)
  url.searchParams.set('code_challenge', challenge)
  url.searchParams.set('code_challenge_method', 'S256')
  url.searchParams.set('tenantId', tenantId)
  return url.toString()
}

export function consumeOAuthContext() {
  const verifier = sessionStorage.getItem(verifierKey)
  const state = sessionStorage.getItem(stateKey)
  const tenantId = sessionStorage.getItem(tenantKey) ?? 'platform'
  sessionStorage.removeItem(verifierKey)
  sessionStorage.removeItem(stateKey)
  sessionStorage.removeItem(tenantKey)
  return { verifier, state, tenantId }
}

function randomString(length: number) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~'
  const bytes = crypto.getRandomValues(new Uint8Array(length))
  return Array.from(bytes, (item) => alphabet[item % alphabet.length]).join('')
}

async function createCodeChallenge(verifier: string) {
  const encoder = new TextEncoder()
  const digest = await crypto.subtle.digest('SHA-256', encoder.encode(verifier))
  return base64UrlEncode(new Uint8Array(digest))
}

function base64UrlEncode(bytes: Uint8Array) {
  const binary = Array.from(bytes, (item) => String.fromCharCode(item)).join('')
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}
