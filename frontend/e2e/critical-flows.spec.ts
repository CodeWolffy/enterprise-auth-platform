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

    await page.goto('/system/audit')

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
      grants: ['tenant:read', 'audit:read'],
      menus: [
        { id: 25, code: 'audit', title: '安全审计', path: '/system/audit', component: 'AuditView', routeKey: 'audit' },
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

      if (url.pathname === '/api/audit/events' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ total: 0, page: 1, size: 20, records: [] }))
        return
      }

      if (url.pathname === '/api/audit/exports/policy' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ retentionDays: 7, maxTasks: 100 }))
        return
      }

      if (url.pathname === '/api/audit/exports' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ total: 0, page: 1, size: 10, records: [] }))
        return
      }

      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/system/users')
    await expect(page).toHaveURL(/\/system\/users$/)
    await expect(page.locator('.sidebar-menu')).toContainText('用户管理')

    await page.locator('.tenant-selector .el-select').click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: '租户 A (tenant-a)' }).click()

    await expect(page).toHaveURL(/\/system\/audit$/)
    await expect(page.locator('.sidebar-menu')).toContainText('安全审计')
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
      grants: ['audit:read'],
      menus: [
        { id: 25, code: 'audit', title: '安全审计', path: '/system/audit', component: 'AuditView', routeKey: 'audit' },
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
      if (url.pathname === '/api/audit/events' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ total: 0, page: 1, size: 20, records: [] }))
        return
      }
      if (url.pathname === '/api/audit/exports/policy' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ retentionDays: 7, maxTasks: 100 }))
        return
      }
      if (url.pathname === '/api/audit/exports' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ total: 0, page: 1, size: 10, records: [] }))
        return
      }
      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/system/audit')

    await expect(page.locator('.tenant-selector')).toHaveCount(0)
  })

  test('导出任务链路：查看详情、归档、删除', async ({ page }) => {
    await seedAuthSession(page)

    let tasks = [
      {
        id: 101,
        tenantId: 'platform',
        operator: 'admin',
        status: 'SUCCESS',
        archived: false,
        archivable: true,
        fileName: 'audit-101.csv',
        recordCount: 88,
        progressPercent: 100,
        progressStage: 'completed',
        retentionExpired: false,
        retentionSummary: '7 天后过期',
        expiresAt: '2099-01-01T00:00:00',
        requestedAt: '2026-03-20T10:00:00',
        completedAt: '2026-03-20T10:02:00',
        errorMessage: null,
      },
    ]

    await page.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const method = request.method()
      if (!url.pathname.startsWith('/api/')) {
        await route.continue()
        return
      }
      if (url.pathname === '/api/audit/events' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          total: 1,
          page: 1,
          size: 20,
          records: [
            {
              type: 'USER_UPDATED',
              operator: 'admin',
              tenantId: 'platform',
              requestId: 'req-001',
              clientIp: '127.0.0.1',
              occurredAt: '2026-03-20T10:00:00',
              details: { diff: ['username'] },
            },
          ],
        }))
        return
      }

      if (url.pathname === '/api/audit/exports/policy' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ retentionDays: 7, maxTasks: 100 }))
        return
      }

      if (url.pathname === '/api/audit/exports' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          total: tasks.length,
          page: 1,
          size: 10,
          records: tasks,
        }))
        return
      }

      if (/^\/api\/audit\/exports\/\d+\/archive$/.test(url.pathname) && method === 'POST') {
        const taskId = Number(url.pathname.split('/')[4])
        tasks = tasks.map((item) =>
          item.id === taskId ? { ...item, archived: true, archivable: false, status: 'ARCHIVED' } : item,
        )
        await fulfillJson(route, 200, apiEnvelope(tasks.find((item) => item.id === taskId)))
        return
      }

      if (/^\/api\/audit\/exports\/\d+$/.test(url.pathname) && method === 'DELETE') {
        const taskId = Number(url.pathname.split('/')[4])
        tasks = tasks.filter((item) => item.id !== taskId)
        await route.fulfill({
          status: 204,
          headers: {
            'access-control-allow-origin': '*',
            'access-control-allow-headers': '*',
            'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
          },
        })
        return
      }

      if (url.pathname === '/api/auth/logout' && method === 'POST') {
        await route.fulfill({
          status: 204,
          headers: {
            'access-control-allow-origin': '*',
            'access-control-allow-headers': '*',
            'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
          },
        })
        return
      }

      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/system/audit')
    await expect(page.locator('[data-testid="audit-task-detail"]')).toHaveCount(1)

    await page.locator('[data-testid="audit-task-detail"]').first().click()
    await expect(page.getByText('导出任务详情')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByText('导出任务详情')).toBeHidden()

    await page.locator('[data-testid="audit-task-archive"]').first().click()
    await expect(page.getByText('已归档').first()).toBeVisible()

    await page.locator('[data-testid="audit-task-delete"]').first().click()
    await page.locator('.el-message-box__btns .el-button--primary').click()
    await expect(page.locator('[data-testid="audit-task-detail"]')).toHaveCount(0)
  })

  test('异步导出：设置时间范围后创建任务并刷新列表', async ({ page }) => {
    await seedAuthSession(page)

    let tasks = [
      {
        id: 201,
        tenantId: 'platform',
        operator: 'admin',
        status: 'SUCCESS',
        archived: false,
        archivable: true,
        fileName: 'audit-201.csv',
        recordCount: 12,
        progressPercent: 100,
        progressStage: 'completed',
        retentionExpired: false,
        retentionSummary: '7 天后过期',
        expiresAt: '2099-01-01T00:00:00',
        requestedAt: '2026-03-20T10:00:00',
        completedAt: '2026-03-20T10:02:00',
        errorMessage: null,
      },
    ]

    await page.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const method = request.method()
      if (!url.pathname.startsWith('/api/')) {
        await route.continue()
        return
      }
      if (url.pathname === '/api/audit/events' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          total: 1,
          page: 1,
          size: 20,
          records: [
            {
              type: 'USER_UPDATED',
              operator: 'admin',
              tenantId: 'platform',
              requestId: 'req-201',
              clientIp: '127.0.0.1',
              occurredAt: '2026-03-20T10:00:00',
              details: { diff: ['email'] },
            },
          ],
        }))
        return
      }

      if (url.pathname === '/api/audit/exports/policy' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ retentionDays: 7, maxTasks: 100 }))
        return
      }

      if (url.pathname === '/api/audit/exports' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          total: tasks.length,
          page: 1,
          size: 10,
          records: tasks,
        }))
        return
      }

      if (url.pathname === '/api/audit/exports' && method === 'POST') {
        tasks = [
          {
            id: 202,
            tenantId: 'platform',
            operator: 'admin',
            status: 'PENDING',
            archived: false,
            archivable: false,
            fileName: 'audit-202.csv',
            recordCount: 0,
            progressPercent: 0,
            progressStage: 'queued',
            retentionExpired: false,
            retentionSummary: '完成后开始计算保留期',
            expiresAt: null,
            requestedAt: '2026-03-21T09:00:00',
            completedAt: null,
            errorMessage: null,
          },
          ...tasks,
        ]
        await fulfillJson(route, 200, apiEnvelope(tasks[0]))
        return
      }

      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/system/audit')
    await expect(page.locator('[data-testid="audit-task-detail"]')).toHaveCount(1)

    await page.getByPlaceholder('开始时间').fill('2026-03-01T00:00:00')
    await page.getByPlaceholder('结束时间').fill('2026-03-02T00:00:00')
    await page.keyboard.press('Enter')

    await page.locator('[data-testid="audit-export-async"]').click()
    await expect(page.locator('[data-testid="audit-task-detail"]')).toHaveCount(2)
    await expect(page.getByText('queued').first()).toBeVisible()
  })

  test('tenant-catalog: 两个 tab 都能持久化列偏好', async ({ page }) => {
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
            enabled: true,
            capabilityCodes: ['audit'],
            referencedTenantCount: 1,
            referencedTenantIds: ['tenant-a'],
          },
        ]))
        return
      }

      if (url.pathname === '/api/tenant-catalog/capabilities' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope([
          {
            id: 11,
            tenantId: 'platform',
            capabilityCode: 'audit',
            capabilityName: '审计',
            capabilityDesc: '审计查询与导出',
            sortOrder: 10,
            enabled: true,
            referencedPackageCount: 1,
            referencedPackageCodes: ['pkg-standard'],
            referencedTenantCount: 0,
            referencedTenantIds: [],
            overrideReferenceCount: 0,
          },
        ]))
        return
      }

      await fulfillJson(route, 200, apiEnvelope({}))
    })

    await page.goto('/platform/tenant-catalog')
    await expect(page.getByText('套餐定义')).toBeVisible()

    await page.getByRole('button', { name: '列显示' }).first().click()
    await page.locator('.column-chooser .el-checkbox').filter({ hasText: '套餐说明' }).click()
    await page.keyboard.press('Escape')
    await expect(page.locator('.el-table th').filter({ hasText: '套餐说明' })).toHaveCount(0)

    await page.getByRole('tab', { name: '能力管理' }).click()
    await page.getByRole('button', { name: '列显示' }).first().click()
    await page.locator('.column-chooser .el-checkbox').filter({ hasText: '排序' }).click()
    await page.keyboard.press('Escape')
    await expect(page.locator('.el-table th').filter({ hasText: '排序' })).toHaveCount(0)

    await page.reload()
    await expect(page.locator('.el-table th').filter({ hasText: '套餐说明' })).toHaveCount(0)
    await expect
      .poll(async () =>
        page.evaluate(() => {
          const raw = window.localStorage.getItem('eap.table.tenant.catalog.packages')
          return raw ? JSON.parse(raw) : null
        }),
      )
      .toMatchObject({ visibleColumns: expect.not.arrayContaining(['packageDesc']) })

    await page.getByRole('tab', { name: '能力管理' }).click()
    await expect(page.locator('.el-table th').filter({ hasText: '排序' })).toHaveCount(0)
    await expect
      .poll(async () =>
        page.evaluate(() => {
          const raw = window.localStorage.getItem('eap.table.tenant.catalog.capabilities')
          return raw ? JSON.parse(raw) : null
        }),
      )
      .toMatchObject({ visibleColumns: expect.not.arrayContaining(['sortOrder']) })
  })
})
