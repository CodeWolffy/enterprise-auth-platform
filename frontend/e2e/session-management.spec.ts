import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'
import { apiEnvelope, defaultSnapshot, fulfillJson, seedAuthSession } from './helpers'

const sessions = [
  {
    sessionId: 'current-session',
    username: 'admin',
    tenantId: 'platform',
    clientIp: '127.0.0.1',
    device: 'Mozilla/5.0 Chrome/120',
    issuedAt: 1773997200000,
    expiresAt: 1774087200000,
    lastAccessAt: 1774000800000,
    active: true,
    currentSession: true,
  },
  {
    sessionId: 'other-session',
    username: 'admin',
    tenantId: 'platform',
    clientIp: '10.0.0.2',
    device: 'Java-http-client',
    issuedAt: 1773990000000,
    expiresAt: 1774080000000,
    lastAccessAt: 1773993600000,
    active: true,
    currentSession: false,
  },
]

async function mockSessionApis(page: Page, onSessionsRequest?: (url: URL, authorization: string | null) => void, onOffline?: (sessionId: string, authorization: string | null) => void) {
  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()

    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }

    if (url.pathname === '/api/auth/me' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope(defaultSnapshot()))
      return
    }

    if (url.pathname === '/api/tenants' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope({ total: 0, page: 1, size: 200, records: [] }))
      return
    }

    if (url.pathname === '/api/auth/sessions' && method === 'GET') {
      onSessionsRequest?.(url, request.headers().authorization ?? null)
      await fulfillJson(route, 200, apiEnvelope(sessions))
      return
    }

    const offlineMatch = url.pathname.match(/^\/api\/auth\/sessions\/([^/]+)\/offline$/)
    if (offlineMatch && method === 'POST') {
      onOffline?.(offlineMatch[1], request.headers().authorization ?? null)
      await fulfillJson(route, 200, apiEnvelope({}))
      return
    }

    if (url.pathname === '/api/auth/logout' && method === 'POST') {
      await fulfillJson(route, 200, apiEnvelope({}))
      return
    }

    await fulfillJson(route, 200, apiEnvelope({}))
  })
}

test.describe('session management', () => {
  test('header online device dialog uses Bearer token and only loads own sessions', async ({ page }) => {
    const sessionRequests: Array<{ scope: string | null; authorization: string | null }> = []
    const offlineRequests: Array<{ sessionId: string; authorization: string | null }> = []

    await seedAuthSession(page)
    await mockSessionApis(
      page,
      (url, authorization) => sessionRequests.push({ scope: url.searchParams.get('scope'), authorization }),
      (sessionId, authorization) => offlineRequests.push({ sessionId, authorization }),
    )

    await page.goto('/dashboard')
    await page.locator('[data-testid="header-online-devices"]').click()

    await expect(page.locator('[data-testid="session-dialog-table"]')).toBeVisible()
    await expect(page.getByText('共 2 个在线会话')).toBeVisible()
    expect(sessionRequests).toContainEqual({ scope: 'own', authorization: 'Bearer e2e-token' })

    const offlineButtons = page.locator('[data-testid="session-dialog-force-offline"]')
    await expect(offlineButtons).toHaveCount(2)
    await expect(offlineButtons.first()).toBeDisabled()
    await expect(offlineButtons.nth(1)).toBeEnabled()

    await offlineButtons.nth(1).click()
    await page.getByRole('button', { name: /^(确定|OK)$/ }).click()

    await expect.poll(() => offlineRequests).toEqual([
      { sessionId: 'other-session', authorization: 'Bearer e2e-token' },
    ])
  })

  test('online users page requests all sessions with Bearer token', async ({ page }) => {
    const sessionRequests: Array<{ scope: string | null; authorization: string | null }> = []

    await seedAuthSession(page)
    await mockSessionApis(page, (url, authorization) => sessionRequests.push({ scope: url.searchParams.get('scope'), authorization }))

    await page.goto('/system/online-users')

    await expect(page.locator('[data-testid="online-users-table"]')).toBeVisible()
    await expect(page.locator('[data-testid="online-users-force-offline"]')).toHaveCount(2)
    expect(sessionRequests).toContainEqual({ scope: 'all', authorization: 'Bearer e2e-token' })
  })
})
