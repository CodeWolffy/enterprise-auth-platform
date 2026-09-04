import type { Page } from 'playwright';

import { expect, test } from 'playwright/test';

/**
 * Opt-in environment:
 * - E2E_SMOKE=1 enables the suite.
 * - E2E_BACKEND=1 confirms that the configured backend is available for API
 *   assertions; tests that need it remain skipped otherwise.
 * - E2E_BASE_URL points to the running web app (default: 127.0.0.1:5777).
 * - E2E_WEB_SERVER_COMMAND optionally starts the web app before tests.
 * - E2E_LOGIN_PAYLOAD supplies a disposable login JSON payload containing a valid
 *   slider-captcha verification for the optional login endpoint check.
 * - E2E_ACCESS_TOKEN optionally supplies a bearer token for API-level workflow smoke.
 * - E2E_TENANT_ID optionally supplies the tenant header for API-level smoke.
 * - E2E_WORKFLOW_START_PAYLOAD/E2E_WORKFLOW_TASK_ID enable disposable workflow
 *   start and approval checks.
 * - E2E_ENABLE_MUTATIONS=1 plus E2E_DATASOURCE_NAME/E2E_SESSION_ID enables
 *   authorization and force-offline checks against disposable test data.
 * - E2E_CODEGEN_TABLE_NAME enables the optional code-generation navigation.
 * - E2E_ENABLE_CODEGEN_DOWNLOAD=1 enables the disposable ZIP download check.
 */

const smokeEnabled = process.env.E2E_SMOKE === '1';
const backendEnabled = process.env.E2E_BACKEND === '1';
const loginPayload = process.env.E2E_LOGIN_PAYLOAD ?? '';
const accessToken = process.env.E2E_ACCESS_TOKEN ?? '';
const tenantId = process.env.E2E_TENANT_ID ?? '';
const workflowStartPayload = process.env.E2E_WORKFLOW_START_PAYLOAD ?? '';
const workflowTaskId = process.env.E2E_WORKFLOW_TASK_ID ?? '';
const storageStatePath = process.env.E2E_STORAGE_STATE;
const mutationsEnabled = process.env.E2E_ENABLE_MUTATIONS === '1';

const targetTenantId = process.env.E2E_TARGET_TENANT_ID ?? '';
const roleCode = process.env.E2E_ROLE_CODE ?? '';
const dataSourceName = process.env.E2E_DATASOURCE_NAME ?? '';
const sessionId = process.env.E2E_SESSION_ID ?? '';
const codegenTableName = process.env.E2E_CODEGEN_TABLE_NAME ?? '';
const codegenDownloadEnabled = process.env.E2E_ENABLE_CODEGEN_DOWNLOAD === '1';

async function openAuthenticatedPage(page: Page, path: string) {
  await page.goto(path);
  await expect(page).not.toHaveURL(/#\/login(?:\?|$)/);
}

function authenticatedApiHeaders() {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${accessToken}`,
  };
  if (tenantId) {
    headers['X-Tenant-Id'] = tenantId;
  }
  return headers;
}

test.describe('public authentication smoke', () => {
  test.skip(
    !smokeEnabled || !backendEnabled,
    'Set E2E_SMOKE=1 and E2E_BACKEND=1 for backend-backed authentication smoke.',
  );

  test('login page requests a behavior captcha', async ({ page, request }) => {
    await page.goto('/#/login');
    await expect(page.locator('input').first()).toBeVisible();

    const response = await request.get('/api/auth/captcha');
    expect(response.ok()).toBeTruthy();
    const payload = (await response.json()) as {
      captchaId?: string;
      data?: {
        backgroundImage?: string;
        captchaId?: string;
        sliderImage?: string;
      };
    };
    const captcha = payload.data ?? payload;
    expect(captcha).toEqual(
      expect.objectContaining({
        backgroundImage: expect.any(String),
        captchaId: expect.any(String),
        sliderImage: expect.any(String),
      }),
    );
  });

  test('login endpoint accepts a configured captcha payload', async ({
    request,
  }) => {
    test.skip(
      !loginPayload,
      'Set E2E_LOGIN_PAYLOAD to exercise the real login endpoint with disposable credentials.',
    );

    const response = await request.post('/api/auth/login', {
      data: JSON.parse(loginPayload) as Record<string, unknown>,
    });
    expect(response.ok()).toBeTruthy();
    const payload = (await response.json()) as { code?: string };
    expect(payload.code).toBe('OK');
  });
});

test.describe('authenticated business smoke', () => {
  test.skip(
    !smokeEnabled || !backendEnabled,
    'Set E2E_SMOKE=1 and E2E_BACKEND=1 for backend-backed smoke tests.',
  );
  test.skip(
    !storageStatePath,
    'Set E2E_STORAGE_STATE to a login storage-state file for authenticated smoke tests.',
  );

  if (storageStatePath) {
    test.use({ storageState: storageStatePath });
  }

  test('tenant switcher can change the active tenant', async ({ page }) => {
    test.skip(
      !targetTenantId,
      'Set E2E_TARGET_TENANT_ID to exercise tenant switching.',
    );

    await openAuthenticatedPage(page, '/#/platform/tenants');
    const switcher = page.locator('.tenant-switcher-button');
    await expect(switcher).toBeVisible();
    await switcher.click();

    const target = page
      .getByRole('menuitem')
      .filter({ hasText: targetTenantId })
      .first();
    await expect(target).toBeVisible();
    await expect(target).toBeEnabled();
    await target.click();
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('.tenant-switcher-button')).toContainText(
      targetTenantId,
    );
  });

  test('a configured role opens the user-role-menu authorization workspace', async ({
    page,
  }) => {
    test.skip(
      !roleCode,
      'Set E2E_ROLE_CODE to exercise user-role-menu authorization coverage.',
    );

    await openAuthenticatedPage(page, '/#/system/roles');
    const row = page.locator('tr').filter({ hasText: roleCode }).first();
    await expect(row).toBeVisible();
    await row.getByRole('button', { exact: true, name: '分配菜单' }).click();
    await expect(
      page.getByRole('dialog').filter({ hasText: '分配菜单' }).last(),
    ).toBeVisible();
  });

  test('data-source authorization is reflected in the list', async ({
    page,
  }) => {
    test.skip(
      !mutationsEnabled || !dataSourceName,
      'Set E2E_ENABLE_MUTATIONS=1 and E2E_DATASOURCE_NAME for authorization smoke.',
    );

    await openAuthenticatedPage(page, '/#/platform/codegen/datasource');
    const row = page.locator('tr').filter({ hasText: dataSourceName }).first();
    await expect(row).toBeVisible();

    const authorizeButton = row.getByRole('button', {
      exact: true,
      name: '授权',
    });
    if (await authorizeButton.count()) {
      await authorizeButton.click();
    }
    await expect(row).toContainText('已授权');
  });

  test('an online session can be forced offline', async ({ page }) => {
    test.skip(
      !mutationsEnabled || !sessionId,
      'Set E2E_ENABLE_MUTATIONS=1 and E2E_SESSION_ID for force-offline smoke.',
    );

    await openAuthenticatedPage(page, '/#/system/online-users');
    const row = page.locator('tr').filter({ hasText: sessionId }).first();
    await expect(row).toBeVisible();
    await row.getByRole('button', { exact: true, name: '强退用户' }).click();

    const dialog = page.getByRole('dialog').filter({ hasText: '强退' }).last();
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { exact: true, name: '确认' }).click();
    await expect(
      page.locator('tr').filter({ hasText: sessionId }).first(),
    ).toHaveCount(0);
  });

  test('workflow start endpoint accepts a disposable request', async ({
    request,
  }) => {
    test.skip(
      !accessToken || !workflowStartPayload,
      'Set E2E_ACCESS_TOKEN and E2E_WORKFLOW_START_PAYLOAD for workflow start smoke.',
    );

    const response = await request.post('/api/workflow/instances', {
      headers: authenticatedApiHeaders(),
      data: JSON.parse(workflowStartPayload) as Record<string, unknown>,
    });
    expect(response.ok()).toBeTruthy();
    const payload = (await response.json()) as { code?: string };
    expect(payload.code).toBe('OK');
  });

  test('workflow approval endpoint accepts a disposable task', async ({
    request,
  }) => {
    test.skip(
      !accessToken || !workflowTaskId,
      'Set E2E_ACCESS_TOKEN and E2E_WORKFLOW_TASK_ID for workflow approval smoke.',
    );

    const response = await request.put(
      `/api/workflow/tasks/${encodeURIComponent(workflowTaskId)}/approve`,
      {
        headers: authenticatedApiHeaders(),
        data: { comment: 'E2E smoke approval' },
      },
    );
    expect(response.ok()).toBeTruthy();
    const payload = (await response.json()) as { code?: string };
    expect(payload.code).toBe('OK');
  });

  test('workflow definitions page is available', async ({ page }) => {
    await openAuthenticatedPage(page, '/#/workflow/definitions');
    await expect(
      page.getByText('流程定义', { exact: true }).first(),
    ).toBeVisible();
  });

  test('code-generation workspace is available', async ({ page }) => {
    await openAuthenticatedPage(page, '/#/platform/codegen/gen-table');
    await expect(page.getByText('数据源表', { exact: true })).toBeVisible();
    await expect(page.getByText('已导入表', { exact: true })).toBeVisible();
  });

  test('an imported table opens the code-generation flow', async ({ page }) => {
    test.skip(
      !codegenTableName,
      'Set E2E_CODEGEN_TABLE_NAME to exercise the optional generation flow.',
    );

    await openAuthenticatedPage(page, '/#/platform/codegen/gen-table');
    await page.getByRole('tab', { exact: true, name: '已导入表' }).click();
    const row = page
      .locator('tr')
      .filter({ hasText: codegenTableName })
      .first();
    await expect(row).toBeVisible();
    await row.getByRole('button', { exact: true, name: '生成代码' }).click();
    await expect(page).toHaveURL(/#\/platform\/codegen\/generate/);
    await expect(
      page.getByRole('button', { exact: true, name: '预览代码' }),
    ).toBeVisible();
    await page.getByRole('button', { exact: true, name: '预览代码' }).click();
    await expect(page.getByText('代码预览', { exact: true })).toBeVisible();
  });

  test('code-generation flow downloads a configured ZIP package', async ({
    page,
  }) => {
    test.skip(
      !codegenTableName || !codegenDownloadEnabled,
      'Set E2E_CODEGEN_TABLE_NAME and E2E_ENABLE_CODEGEN_DOWNLOAD=1 for the ZIP download smoke.',
    );

    await openAuthenticatedPage(page, '/#/platform/codegen/gen-table');
    await page.getByRole('tab', { exact: true, name: '已导入表' }).click();
    const row = page
      .locator('tr')
      .filter({ hasText: codegenTableName })
      .first();
    await expect(row).toBeVisible();
    await row.getByRole('button', { exact: true, name: '生成代码' }).click();
    await expect(page).toHaveURL(/#\/platform\/codegen\/generate/);

    const downloadPromise = page.waitForEvent('download');
    await page
      .getByRole('button', { exact: true, name: '生成代码并下载' })
      .click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.zip$/i);
  });
});
