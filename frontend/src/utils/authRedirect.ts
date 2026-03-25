import { createOAuthRedirect } from './oauth'

let redirecting = false

export async function redirectToAuthorizationPage(tenantId?: string) {
  if (typeof window === 'undefined' || redirecting) {
    return
  }
  redirecting = true
  try {
    const authorizeUrl = await createOAuthRedirect(tenantId)
    window.location.href = authorizeUrl
  } catch {
    window.location.href = '/login'
  }
}

