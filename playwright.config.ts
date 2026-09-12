import { defineConfig } from '@playwright/test';

const workspaceId = process.env.E2E_WORKSPACE_ID ?? 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa';
const userId = process.env.E2E_USER_ID ?? 'bbbbbbbb-2222-4222-8222-bbbbbbbbbbbb';

export default defineConfig({
  testDir: './e2e',
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://127.0.0.1:3000',
    extraHTTPHeaders: {
      'x-user-id': userId,
      'x-workspace-id': workspaceId,
    },
    viewport: { width: 390, height: 844 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
