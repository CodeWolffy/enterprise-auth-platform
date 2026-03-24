import { createOAuthRedirect } from './oauth'

let redirecting = false

export async function redirectToAuthorizationPage(tenantId?: string) {
  if (typeof window === 'undefined' || redirecting) {
    return
  }
  redirecting = true
  const targetTenant = tenantId && tenantId.trim() ? tenantId.trim() : 'platform'
  try {
    const authorizeUrl = await createOAuthRedirect(targetTenant)
    window.location.href = authorizeUrl
  } catch {
    window.location.href = '/login'
  }
}

