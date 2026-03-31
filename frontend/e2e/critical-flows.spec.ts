import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'
import { apiEnvelope, AUTH_STORAGE_KEY, defaultSnapshot, fulfillJson, seedAuthSession } from './helpers'

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
  const inputs = page.locator('.auth-panel--form input')
  await inputs.nth(0).fill('platform')
  await inputs.nth(1).fill('admin')
  await inputs.nth(2).fill('Admin@123456')
  await inputs.nth(3).fill('2468')
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
        await fulfillJson(route, 200, apiEnvelope({
          captchaId: 'captcha-e2e',
          expiresAt: Date.now() + 300000,
          previewCode: '2468',
        }))
        return
      }

      if (url.pathname === '/api/auth/csrf' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-e2e-token',
        }))
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

    await expect.poll(() => loginPayload).toContain('"tenantId":"platform"')
    await expect.poll(() => loginPayload).toContain('"username":"admin"')
    await expect.poll(() => loginPayload).toContain('"password":"Admin@123456"')
    await expect.poll(() => loginPayload).toContain('"captchaId":"captcha-e2e"')
    await expect.poll(() => loginPayload).toContain('"captchaCode":"2468"')

    await expect(page).toHaveURL(/\/dashboard$/)
    await expect(page.locator('[data-testid="logout-button"]')).toBeVisible()
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
      await fulfillJson(route, 200, apiEnvelope({
        captchaId: 'captcha-restore',
        expiresAt: Date.now() + 300000,
        previewCode: '2468',
      }))
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
      if (url.pathname.startsWith('/api/auth/csrf') && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-e2e-token',
        }))
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
      if (url.pathname.startsWith('/api/auth/csrf') && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-e2e-token',
        }))
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

    await page.goto('/system/settings/tenant-catalog')
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
