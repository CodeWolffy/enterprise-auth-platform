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

  await page.route('**/oauth2/token', async (route) => {
    await route.fulfill({
      status: 200,
      headers: {
        'access-control-allow-origin': '*',
        'access-control-allow-headers': '*',
        'access-control-allow-methods': 'GET,POST,PUT,DELETE,OPTIONS',
      },
      contentType: 'application/json',
      body: JSON.stringify({
        access_token: 'callback-token',
        refresh_token: 'callback-refresh',
        token_type: 'Bearer',
        expires_in: 3600,
      }),
    })
  })

  await page.route('**/api/auth/me', async (route) => {
    await fulfillJson(route, 200, apiEnvelope(snapshot))
  })

  await page.goto('/auth/callback?code=mock-code&state=state-e2e')
  await expect(page).toHaveURL(/\/dashboard$/)
}

test.describe('关键流程回归', () => {
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
      await fulfillJson(route, 401, {
        code: '401',
        success: false,
        data: null,
        message: 'unauthorized',
      })
    })

    await page.goto('/system/audit')
    await expect(page).toHaveURL(/\/login$/)
    await expect
      .poll(async () => page.evaluate((key) => window.localStorage.getItem(key), AUTH_STORAGE_KEY))
      .toBeNull()
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

    await page.getByPlaceholder('开始时间').fill('2026-03-01 00:00:00')
    await page.getByPlaceholder('结束时间').fill('2026-03-02 00:00:00')
    await page.keyboard.press('Enter')

    await page.locator('[data-testid="audit-export-async"]').click()
    await expect(page.locator('[data-testid="audit-task-detail"]')).toHaveCount(2)
    await expect(page.getByText('queued').first()).toBeVisible()
  })
})
