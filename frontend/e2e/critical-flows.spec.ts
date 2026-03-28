import type { Page } from '@playwright/test'
import { expect, test } from '@playwright/test'
import { apiEnvelope, AUTH_STORAGE_KEY, defaultSnapshot, fulfillJson } from './helpers'

async function loginByCallback(page: Page) {
  const snapshot = defaultSnapshot()

  await page.addInitScript(() => {
    window.sessionStorage.setItem('eap.oauth.verifier', 'verifier-e2e')
    window.sessionStorage.setItem('eap.oauth.state', 'state-e2e')
    window.sessionStorage.setItem('eap.oauth.tenant', 'platform')
  })

  await page.route('**/api/auth/csrf*', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'access-control-allow-origin': '*',
        'access-control-allow-headers': '*',
        'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
      },
      contentType: 'application/json',
      body: JSON.stringify(
        apiEnvelope({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-e2e-token',
        }),
      ),
    })
  })

  await page.route('**/api/auth/oauth/exchange*', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'access-control-allow-origin': '*',
        'access-control-allow-headers': '*',
        'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
      },
      contentType: 'application/json',
      body: JSON.stringify(
        apiEnvelope({
          tenantId: 'platform',
          sessionId: 'oauth-session-e2e',
          expiresAt: '2099-01-01T00:00:00Z',
        }),
      ),
    })
  })

  await page.route('**/api/auth/oauth/refresh*', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'access-control-allow-origin': '*',
        'access-control-allow-headers': '*',
        'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
      },
      contentType: 'application/json',
      body: JSON.stringify(
        apiEnvelope({
          tenantId: 'platform',
          sessionId: 'oauth-session-e2e',
          expiresAt: '2099-01-01T00:00:00Z',
        }),
      ),
    })
  })

  await page.route('**/api/auth/me', async (route) => {
    await fulfillJson(route, 200, apiEnvelope(snapshot))
  })

  await page.goto('/auth/callback?code=mock-code&state=state-e2e')
  await expect(page).toHaveURL(/\/dashboard$/)
}

test.describe('关键流程回归', () => {
  test('前端登录页：携带 OAuth 参数时展示账号密码并提交 /login', async ({ page }) => {
    let loginPayload = ''

    await page.route('**/login', async (route) => {
      loginPayload = route.request().postData() ?? ''
      await route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: '<html><body>ok</body></html>',
      })
    })

    await page.goto('/login?response_type=code&client_id=eap-frontend-spa&state=state-e2e&tenantId=platform')
    await expect(page.getByRole('heading', { level: 2, name: '登录并继续授权' })).toBeVisible()

    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('password-123')
    await page.locator('[data-testid="login-submit"]').click()

    await expect.poll(() => loginPayload).toContain('tenantId=platform')
    await expect.poll(() => loginPayload).toContain('username=admin')
    await expect.poll(() => loginPayload).toContain('password=password-123')
    await expect.poll(() => loginPayload).toContain('client_id=eap-frontend-spa')
  })

  test('前端登录页：密码框回车只提交一次 /login', async ({ page }) => {
    let submitCount = 0
    let loginPayload = ''

    await page.route('**/login', async (route) => {
      submitCount += 1
      loginPayload = route.request().postData() ?? ''
      await route.fulfill({
        status: 200,
        contentType: 'text/html',
        body: '<html><body>ok</body></html>',
      })
    })

    await page.goto('/login?response_type=code&client_id=eap-frontend-spa&state=state-e2e&tenantId=platform')
    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('password-123')
    await page.getByPlaceholder('请输入密码').press('Enter')

    await expect.poll(() => submitCount).toBe(1)
    await expect.poll(() => loginPayload).toContain('username=admin')
    await expect.poll(() => loginPayload).toContain('password=password-123')
    await expect.poll(() => loginPayload).toContain('tenantId=platform')
  })

  test('登录回调流程：成功换 token 后进入控制台', async ({ page }) => {
    await loginByCallback(page)
    await expect(page.locator('[data-testid="logout-button"]')).toBeVisible()
  })

  test('会话失效流程：接口 401 后清理会话并回到登录页', async ({ page }) => {
    await loginByCallback(page)
    await page.unroute('**/api/auth/me')

    await page.route('**/*', async (route) => {
      const pathname = new URL(route.request().url()).pathname
      if (!pathname.startsWith('/api/')) {
        await route.continue()
        return
      }
      if (pathname.startsWith('/api/auth/csrf')) {
        await fulfillJson(
          route,
          200,
          apiEnvelope({
            headerName: 'X-XSRF-TOKEN',
            parameterName: '_csrf',
            token: 'csrf-e2e-token',
          }),
        )
        return
      }
      await fulfillJson(route, 401, {
        code: '401',
        success: false,
        data: null,
        message: 'unauthorized',
      })
    })

    await page.goto('/system/audit')
    await expect
      .poll(async () => page.evaluate((key) => window.sessionStorage.getItem(key), AUTH_STORAGE_KEY))
      .toBeNull()
    await expect(page).toHaveURL(/\/oauth2\/authorize\?/)
  })

  test('导出任务链路：查看详情 -> 归档 -> 删除', async ({ page }) => {
    await loginByCallback(page)
    await page.unroute('**/api/auth/me')

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
        await fulfillJson(
          route,
          200,
          apiEnvelope({
            headerName: 'X-XSRF-TOKEN',
            parameterName: '_csrf',
            token: 'csrf-e2e-token',
          }),
        )
        return
      }

      if (url.pathname === '/api/audit/events' && method === 'GET') {
        await fulfillJson(
          route,
          200,
          apiEnvelope({
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
          }),
        )
        return
      }

      if (url.pathname === '/api/audit/exports/policy' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ retentionDays: 7, maxTasks: 100 }))
        return
      }

      if (url.pathname === '/api/audit/exports' && method === 'GET') {
        await fulfillJson(
          route,
          200,
          apiEnvelope({
            total: tasks.length,
            page: 1,
            size: 10,
            records: tasks,
          }),
        )
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
    await loginByCallback(page)
    await page.unroute('**/api/auth/me')

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
        await fulfillJson(
          route,
          200,
          apiEnvelope({
            headerName: 'X-XSRF-TOKEN',
            parameterName: '_csrf',
            token: 'csrf-e2e-token',
          }),
        )
        return
      }

      if (url.pathname === '/api/audit/events' && method === 'GET') {
        await fulfillJson(
          route,
          200,
          apiEnvelope({
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
          }),
        )
        return
      }

      if (url.pathname === '/api/audit/exports/policy' && method === 'GET') {
        await fulfillJson(route, 200, apiEnvelope({ retentionDays: 7, maxTasks: 100 }))
        return
      }

      if (url.pathname === '/api/audit/exports' && method === 'GET') {
        await fulfillJson(
          route,
          200,
          apiEnvelope({
            total: tasks.length,
            page: 1,
            size: 10,
            records: tasks,
          }),
        )
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
  test('consents: table preferences persist and revoke flow works', async ({ page }) => {
    await loginByCallback(page)
    await page.unroute('**/api/auth/me')

    let records = [
      {
        registeredClientId: 'client-reg-1',
        tenantId: 'platform',
        clientId: 'eap-web',
        clientName: 'EAP Web',
        principalName: 'alice',
        authorities: ['openid', 'profile'],
        lastGrantedAt: '2026-03-22T10:00:00',
        lastRevokedAt: null,
        auditEventCount: 3,
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
        await fulfillJson(
          route,
          200,
          apiEnvelope({
            headerName: 'X-XSRF-TOKEN',
            parameterName: '_csrf',
            token: 'csrf-e2e-token',
          }),
        )
        return
      }

      if (url.pathname === '/api/auth/consents' && method === 'GET') {
        await fulfillJson(
          route,
          200,
          apiEnvelope({
            total: records.length,
            page: 1,
            size: 10,
            records,
          }),
        )
        return
      }

      if (url.pathname === '/api/auth/consents' && method === 'DELETE') {
        const clientId = url.searchParams.get('registeredClientId')
        const principalName = url.searchParams.get('principalName')
        records = records.filter(
          (item) => !(item.registeredClientId === clientId && item.principalName === principalName),
        )
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

    await page.goto('/system/consents')
    await expect(page.getByText('alice')).toBeVisible()

    await page.getByRole('button', { name: '列显示' }).click()
    await page.locator('.column-chooser .el-checkbox').filter({ hasText: '审计联动' }).click()
    await page.keyboard.press('Escape')
    await expect(page.locator('.el-table th').filter({ hasText: '审计联动' })).toHaveCount(0)

    await page.reload()
    await expect(page.locator('.el-table th').filter({ hasText: '审计联动' })).toHaveCount(0)
    await expect
      .poll(async () =>
        page.evaluate(() => {
          const raw = window.localStorage.getItem('eap.table.consents')
          return raw ? JSON.parse(raw) : null
        }),
      )
      .toMatchObject({ visibleColumns: expect.not.arrayContaining(['audit']) })

    await page.getByRole('button', { name: '撤销授权' }).first().click()
    await page.locator('.el-message-box__btns .el-button--primary').click()
    await expect(page.getByText('alice')).toHaveCount(0)
  })

  test('tenant-catalog: both tab tables persist column preferences', async ({ page }) => {
    await loginByCallback(page)
    await page.unroute('**/api/auth/me')

    await page.route('**/*', async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const method = request.method()
      if (!url.pathname.startsWith('/api/')) {
        await route.continue()
        return
      }

      if (url.pathname === '/api/tenant-catalog/packages' && method === 'GET') {
        await fulfillJson(
          route,
          200,
          apiEnvelope([
            {
              id: 1,
              tenantId: 'platform',
              packageCode: 'pkg-standard',
              packageName: '标准版',
              userQuota: 200,
              storageQuotaGb: 200,
              packageDesc: '适用于常规业务租户',
              enabled: true,
              capabilityCodes: ['oauth', 'audit'],
              referencedTenantCount: 1,
              referencedTenantIds: ['tenant-a'],
            },
          ]),
        )
        return
      }

      if (url.pathname === '/api/tenant-catalog/capabilities' && method === 'GET') {
        await fulfillJson(
          route,
          200,
          apiEnvelope([
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
          ]),
        )
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
