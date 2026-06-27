import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'
import { apiEnvelope, AUTH_STORAGE_KEY, defaultSnapshot, fulfillJson, seedAuthSession } from './helpers'

const captchaSvg = btoa('<svg xmlns="http://www.w3.org/2000/svg" width="300" height="160"><rect width="300" height="160" fill="#f8fafc"/></svg>')
const sliderSvg = btoa('<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48"><rect width="48" height="48" fill="#60a5fa"/></svg>')
const mockCaptchaPayload = {
  captchaId: 'captcha-e2e',
  backgroundImage: `data:image/svg+xml;base64,${captchaSvg}`,
  sliderImage: `data:image/svg+xml;base64,${sliderSvg}`,
  backgroundImageWidth: 300,
  backgroundImageHeight: 160,
  sliderImageWidth: 48,
  sliderImageHeight: 48,
}

async function mockDashboardApis(page: Page) {
  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()

    if (!url.pathname.startsWith('/api/')) {
      await route.continue()
      return
    }

    if (url.pathname === '/api/system/dicts' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope({ total: 2, page: 1, size: 1, records: [] }))
      return
    }

    if (url.pathname === '/api/system/configs' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope({ total: 3, page: 1, size: 1, records: [] }))
      return
    }

    if (url.pathname === '/api/system/notices' && method === 'GET') {
      const published = url.searchParams.get('published') === 'true'
      await fulfillJson(route, 200, apiEnvelope({ total: published ? 1 : 4, page: 1, size: 1, records: [] }))
      return
    }

    if (url.pathname === '/api/system/features' && method === 'GET') {
      await fulfillJson(route, 200, apiEnvelope({
        gatewayEnabled: false,
        nacosEnabled: false,
        mqEnabled: false,
        seataEnabled: false,
        jobEnabled: false,
        lokiEnabled: false,
      }))
      return
    }

    await fulfillJson(route, 200, apiEnvelope({}))
  })
}

async function fillLoginForm(page: Page) {
  await page.locator('[data-testid="login-username"]').fill('admin')
  await page.locator('[data-testid="login-password"]').fill('Admin@123456')
}

async function completeSliderCaptcha(page: Page) {
  const handle = page.locator('[data-testid="captcha-handle"]')
  await expect(handle).toBeVisible()
  const box = await handle.boundingBox()
  if (!box) {
    throw new Error('captcha handle is not visible')
  }
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 + 180, box.y + box.height / 2, { steps: 12 })
  await page.mouse.up()
}

test.describe('关键流程回归', () => {
  test('登录页走当前 Session 登录链路并进入控制台', async ({ page }) => {
    const snapshot = defaultSnapshot()
    let loginPayload = ''

    await page.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const method = request.method()

      if (!url.pathname.startsWith('/api/')) {
        await route.continue()
        return
      }

      if (url.pathname === '/api/auth/captcha' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope(mockCaptchaPayload))
        return
      }

      if (url.pathname === '/api/auth/login' && method === 'POST') {
        loginPayload = request.postData() ?? ''
        await fulfillJson(route, 200, apiEnvelope({
          tenantId: 'platform',
          sessionId: 'session-e2e',
          expiresAt: Date.now() + 3600000,
        }))
        return
      }

      if (url.pathname === '/api/auth/captcha/verify' && method === 'POST') {
        await fulfillJson(route, 200, apiEnvelope({}))
        return
      }

      if (url.pathname === '/api/auth/me' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope(snapshot))
        return
      }

      if (url.pathname === '/api/system/dicts' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ total: 2, page: 1, size: 1, records: [] }))
        return
      }

      if (url.pathname === '/api/system/configs' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ total: 3, page: 1, size: 1, records: [] }))
        return
      }

      if (url.pathname === '/api/system/notices' && method === 'GET') {
        const published = url.searchParams.get('published') === 'true'
        await fulfillJson(route, 200, apiEnvelope({ total: published ? 1 : 4, page: 1, size: 1, records: [] }))
        return
      }

      if (url.pathname === '/api/system/features' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          gatewayEnabled: false,
          nacosEnabled: false,
          mqEnabled: false,
          seataEnabled: false,
          jobEnabled: false,
          lokiEnabled: false,
        }))
        return
      }

      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/login')
    await fillLoginForm(page)
    await page.locator('[data-testid="login-submit"]').click()
    await completeSliderCaptcha(page)

    await expect.poll(() => loginPayload).toContain('"username":"admin"')
    await expect.poll(() => loginPayload).toContain('"password":"Admin@123456"')
    await expect.poll(() => loginPayload).toContain('"captchaId":"captcha-e2e"')
    await expect.poll(() => loginPayload).toContain('"captchaCode":"{')

    await expect(page).toHaveURL(/\/dashboard$/)
    await expect(page.locator('[data-testid="user-menu-button"]')).toBeVisible()
  })

  test('会话恢复失败时清理本地状态并返回登录页', async ({ page }) => {
    await page.route('**/api/auth/me', async (route) => {
      await fulfillJson(route, 401, {
        code: 'SESSION_NOT_FOUND',
        success: false,
        data: null,
        message: 'session missing',
      })
    })

    await page.route('**/api/auth/captcha', async (route) => {
      await fulfillJson(route, 200, apiEnvelope({ ...mockCaptchaPayload, captchaId: 'captcha-restore' }))
    })

    await page.goto('/login')
    await page.evaluate(
      ({ key, value }) => {
        window.sessionStorage.setItem(key, JSON.stringify(value))
      },
      {
        key: AUTH_STORAGE_KEY,
        value: {
          authenticated: true,
          expiresAt: Date.now() + 60 * 60 * 1000,
          tenantId: 'platform',
          operatorTenantId: 'platform',
          snapshot: null,
        },
      },
    )

    await page.goto('/system/logs/operation')

    await expect(page).toHaveURL(/\/login/)
    await expect
      .poll(async () => page.evaluate((key) => window.sessionStorage.getItem(key), AUTH_STORAGE_KEY))
      .toBeNull()
  })

  test('超级管理员可以切换租户并在刷新前持久化目标权限快照', async ({ page }) => {
    const platformSnapshot = defaultSnapshot()
    const tenantSnapshot = {
      ...defaultSnapshot(),
      tenantId: 'tenant-a',
      operatorTenantId: 'platform',
    }
    let switchRequestPath = ''
    let meRequestCount = 0

    await seedAuthSession(page, platformSnapshot)

    await page.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const method = request.method()
      if (!url.pathname.startsWith('/api/')) {
        await route.continue()
        return
      }

      if (url.pathname === '/api/tenants' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          total: 2,
          page: 1,
          size: 200,
          records: [
            { tenantId: 'platform', name: '平台租户', platformLevel: true, tenantStatus: 1 },
            { tenantId: 'tenant-a', name: '租户 A', platformLevel: false, tenantStatus: 1 },
          ],
        }))
        return
      }

      if (/^\/api\/auth\/tenants\/[^/]+\/switch$/.test(url.pathname) && method === 'POST') {
        switchRequestPath = url.pathname
        await new Promise((resolve) => setTimeout(resolve, 650))
        await fulfillJson(route, 200, apiEnvelope(tenantSnapshot))
        return
      }

      if (url.pathname === '/api/auth/me' && method === 'GET') {
        meRequestCount += 1
        await fulfillJson(route, 200, apiEnvelope(platformSnapshot))
        return
      }

      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/dashboard')
    await expect(page.locator('.tenant-selector .el-select')).toBeVisible()

    await page.locator('.tenant-selector .el-select').click()
    const targetTenantOption = page.locator('.el-select-dropdown__item').filter({ hasText: '租户 A (tenant-a)' })
    await expect(targetTenantOption).toBeVisible()
    await targetTenantOption.click()

    await expect.poll(() => switchRequestPath).toBe('/api/auth/tenants/tenant-a/switch')
    expect(meRequestCount).toBe(0)
    await expect
      .poll(async () =>
        page.evaluate((key) => {
          const raw = window.sessionStorage.getItem(key)
          return raw ? JSON.parse(raw).tenantId : null
        }, AUTH_STORAGE_KEY),
      )
      .toBe('tenant-a')
    await expect
      .poll(async () =>
        page.evaluate((key) => {
          const raw = window.sessionStorage.getItem(key)
          return raw ? JSON.parse(raw).snapshot?.tenantId : null
        }, AUTH_STORAGE_KEY),
      )
      .toBe('tenant-a')
    await expect(page.locator('.tenant-selector')).toContainText('tenant-a')
  })

  test('切换租户后按新租户菜单归位且不整页刷新', async ({ page }) => {
    const platformSnapshot = defaultSnapshot()
    const tenantSnapshot = {
      ...defaultSnapshot(),
      tenantId: 'tenant-a',
      operatorTenantId: 'platform',
      grants: ['tenant:read', 'upms:operationlog:page'],
      menus: [
        { id: 29, code: 'operation-logs', title: '操作日志', path: '/system/logs/operation', component: 'OperationLogView', routeKey: 'operation-logs' },
      ],
    }

    await page.addInitScript(() => {
      const reloadCountKey = '__eap_reload_count__'
      const current = Number(window.sessionStorage.getItem(reloadCountKey) || '0')
      window.sessionStorage.setItem(reloadCountKey, String(current + 1))
    })
    await seedAuthSession(page, platformSnapshot)

    await page.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const method = request.method()
      if (!url.pathname.startsWith('/api/')) {
        await route.continue()
        return
      }

      if (url.pathname === '/api/tenants' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          total: 2,
          page: 1,
          size: 200,
          records: [
            { tenantId: 'platform', name: '平台租户', platformLevel: true, tenantStatus: 1 },
            { tenantId: 'tenant-a', name: '租户 A', platformLevel: false, tenantStatus: 1 },
          ],
        }))
        return
      }

      if (url.pathname === '/api/users' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          total: 1,
          page: 1,
          size: 10,
          records: [
            { id: 1, tenantId: 'platform', username: 'admin', displayName: '管理员', enabled: true, roles: ['ADMIN'] },
          ],
        }))
        return
      }

      if (url.pathname === '/api/roles' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope([]))
        return
      }

      if (/^\/api\/auth\/tenants\/[^/]+\/switch$/.test(url.pathname) && method === 'POST') {
        await fulfillJson(route, 200, apiEnvelope(tenantSnapshot))
        return
      }

      if (url.pathname === '/api/logs/operation' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ total: 0, page: 1, size: 20, records: [] }))
        return
      }

      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/system/users')
    await expect(page).toHaveURL(/\/system\/users$/)
    await expect(page.locator('.sidebar-menu')).toContainText('用户管理')

    await page.locator('.tenant-selector .el-select').click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: '租户 A (tenant-a)' }).click()

    await expect(page).toHaveURL(/\/system\/logs\/operation$/)
    await expect(page.locator('.sidebar-menu')).toContainText('操作日志')
    await expect(page.locator('.sidebar-menu')).not.toContainText('用户管理')
    await expect
      .poll(async () => page.evaluate(() => window.sessionStorage.getItem('__eap_reload_count__')))
      .toBe('1')
  })

  test('切换租户失败时恢复旧租户、旧快照和当前路由', async ({ page }) => {
    const platformSnapshot = defaultSnapshot()

    await seedAuthSession(page, platformSnapshot)

    await page.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const method = request.method()
      if (!url.pathname.startsWith('/api/')) {
        await route.continue()
        return
      }

      if (url.pathname === '/api/tenants' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          total: 2,
          page: 1,
          size: 200,
          records: [
            { tenantId: 'platform', name: '平台租户', platformLevel: true, tenantStatus: 1 },
            { tenantId: 'tenant-a', name: '租户 A', platformLevel: false, tenantStatus: 1 },
          ],
        }))
        return
      }

      if (/^\/api\/auth\/tenants\/[^/]+\/switch$/.test(url.pathname) && method === 'POST') {
        await fulfillJson(route, 400, {
          code: 'TENANT_DISABLED',
          success: false,
          data: null,
          message: '租户已停用',
        })
        return
      }

      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/dashboard')
    await page.locator('.tenant-selector .el-select').click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: '租户 A (tenant-a)' }).click()

    await expect(page).toHaveURL(/\/dashboard$/)
    await expect
      .poll(async () =>
        page.evaluate((key) => {
          const raw = window.sessionStorage.getItem(key)
          return raw ? JSON.parse(raw) : null
        }, AUTH_STORAGE_KEY),
      )
      .toMatchObject({
        tenantId: 'platform',
        operatorTenantId: 'platform',
        snapshot: { tenantId: 'platform' },
      })
    await expect(page.locator('.tenant-selector')).toContainText('platform')
  })

  test('非管理员用户不显示租户选择器', async ({ page }) => {
    const tenantSnapshot = {
      ...defaultSnapshot(),
      tenantId: 'tenant-a',
      operatorTenantId: 'tenant-a',
      superAdmin: false,
      grants: ['upms:operationlog:page'],
      menus: [
        { id: 29, code: 'operation-logs', title: '操作日志', path: '/system/logs/operation', component: 'OperationLogView', routeKey: 'operation-logs' },
      ],
    }

    await seedAuthSession(page, tenantSnapshot)
    await page.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const method = request.method()
      if (!url.pathname.startsWith('/api/')) {
        await route.continue()
        return
      }
      if (url.pathname === '/api/logs/operation' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ total: 0, page: 1, size: 20, records: [] }))
        return
      }
      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/system/logs/operation')

    await expect(page.locator('.tenant-selector')).toHaveCount(0)
  })

  test('tenant-catalog: 套餐页展示应用标识与引用租户', async ({ page }) => {
    await seedAuthSession(page)
    await mockDashboardApis(page)

    await page.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const method = request.method()
      if (!url.pathname.startsWith('/api/')) {
        await route.continue()
        return
      }

      if (url.pathname === '/api/tenant-catalog/packages' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope([
          {
            id: 1,
            tenantId: 'platform',
            packageCode: 'pkg-standard',
            packageName: '标准版',
            userQuota: 200,
            storageQuotaGb: 200,
            packageDesc: '适用于常规业务租户',
            appKey: 'upms,workflow',
            enabled: true,
            referencedTenantCount: 1,
            referencedTenantIds: ['tenant-a'],
          },
        ]))
        return
      }

      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/platform/tenant-catalog')
    await expect(page.getByRole('heading', { name: '租户套餐' })).toBeVisible()
    await expect(page.getByText('标准版')).toBeVisible()
    await expect(page.getByText('upms')).toBeVisible()
    await expect(page.getByText('workflow')).toBeVisible()
    await expect(page.locator('.el-table__body')).toContainText('1')
  })
})
