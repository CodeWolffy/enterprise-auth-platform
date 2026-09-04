import { defineConfig, devices } from 'playwright/test';

const baseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5777';
// The runner reuses an already-running app by default. Set this command when
// the browser suite should start a local web server itself.
const webServerCommand = process.env.E2E_WEB_SERVER_COMMAND;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? 'line' : 'list',
  timeout: 30_000,
  expect: {
    timeout: 5000,
  },
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    ...devices['Desktop Chrome'],
  },
  ...(webServerCommand
    ? {
        webServer: {
          command: webServerCommand,
          reuseExistingServer: true,
          timeout: 120_000,
          url: baseURL,
        },
      }
    : {}),
});
